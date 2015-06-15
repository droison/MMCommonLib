package xyz.mumiao.mmbus;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class MMBus {
  public static final String DEFAULT_IDENTIFIER = "defaultBus";

  /** All registered event handlers, indexed by event type. */
  private final ConcurrentMap<String, Set<EventHandler>> handlersByType = new ConcurrentHashMap<String, Set<EventHandler>>();

  /** Identifier used to differentiate the event bus instance. */
  private final String identifier;

  /** Thread enforcer for register, unregister, and posting events. */
  private final ThreadEnforcer enforcer;

  /** Used to find handler methods in register and unregister. */
  private final HandlerFinder handlerFinder;

  /** Queues of events for the current thread to dispatch. */
  private final ThreadLocal<ConcurrentLinkedQueue<EventWithHandler>> eventsToDispatch =
      new ThreadLocal<ConcurrentLinkedQueue<EventWithHandler>>() {
        @Override protected ConcurrentLinkedQueue<EventWithHandler> initialValue() {
          return new ConcurrentLinkedQueue<EventWithHandler>();
        }
      };

  /** True if the current thread is currently dispatching an event. */
  private final ThreadLocal<Boolean> isDispatching = new ThreadLocal<Boolean>() {
    @Override protected Boolean initialValue() {
      return false;
    }
  };


  public MMBus() {
    this(DEFAULT_IDENTIFIER);
  }

  public MMBus(String identifier) {
    this(ThreadEnforcer.MAIN, identifier);
  }

  public MMBus(ThreadEnforcer enforcer) {
    this(enforcer, DEFAULT_IDENTIFIER);
  }

  public MMBus(ThreadEnforcer enforcer, String identifier) {
    this(enforcer, identifier, new DefaultHandlerFinder());
  }

  public MMBus(ThreadEnforcer enforcer, String identifier, HandlerFinder handlerFinder) {
    this.enforcer =  enforcer;
    this.identifier = identifier;
    this.handlerFinder = handlerFinder;
  }

  @Override public String toString() {
    return "[MMBus \"" + identifier + "\"]";
  }

  /**
   *
   * @param keyClass 目标接口
   * @param object 接口对应的实现，具体的要注册的，一个类可以注册和反注册多个接口
   */
  public  <T> void register(Class<T> keyClass, T object) {

    if (!keyClass.isInterface())
      throw new IllegalStateException("register keyClass must be a interface");

    if (object == null) {
      throw new NullPointerException("Object to register must not be null.");
    }
    enforcer.enforce(this);

    Map<String, EventHandler<T>> foundHandlersMap = handlerFinder.findAllSubscribers(keyClass, object);
    for (String type : foundHandlersMap.keySet()) {
      Set<EventHandler> handlers = handlersByType.get(type);
      if (handlers == null) {
        //concurrent put if absent
        Set<EventHandler> handlersCreation = new CopyOnWriteArraySet<EventHandler>();
        handlers = handlersByType.putIfAbsent(type, handlersCreation);
        if (handlers == null) {
          handlers = handlersCreation;
        }
      }
      final EventHandler<T> foundHandlers = foundHandlersMap.get(type);
      handlers.add(foundHandlers);
    }
  }

  /**
   *
   * @param keyClass 目标接口
   * @param object 接口对应的实现，具体的要反注册的，一个类可以注册和反注册多个接口
   */
  public  <T> void unregister(Class<T> keyClass, T object) {

    if (!keyClass.isInterface())
      throw new IllegalStateException("unregister keyClass must be a interface");

    if (object == null) {
      throw new NullPointerException("Object to unregister must not be null.");
    }
    enforcer.enforce(this);

    Map<String, EventHandler<T>> handlersInListener = handlerFinder.findAllSubscribers(keyClass, object);

    for (String key : handlersInListener.keySet()) {
      Set<EventHandler> currentHandlers = getHandlersForEventType(key);

      EventHandler<T> eventMethodsInListener = handlersInListener.get(key);

      if (currentHandlers == null || !currentHandlers.contains(eventMethodsInListener)) {
        throw new IllegalArgumentException("Missing event handler for an annotated method. Is " + object.getClass() + " registered?");
      }

      eventMethodsInListener.invalidate();
      currentHandlers.remove(eventMethodsInListener);
    }
  }

  public void post(Class<?> keyClass, Object event){
    if (!keyClass.isInterface())
      throw new IllegalStateException("post keyClass must be a interface");

    if (event == null) {
      throw new NullPointerException("Event to post must not be null.");
    }

    enforcer.enforce(this);

    Set<Class<?>> dispatchTypes = flattenHierarchy(event.getClass());

    for (Class<?> eventType : dispatchTypes) {
      Set<EventHandler> wrappers = getHandlersForEventType(handlerFinder.keyFromSubscribers(keyClass, eventType));

      if (wrappers != null && !wrappers.isEmpty()) {
        for (EventHandler wrapper : wrappers) {
          enqueueEvent(event, wrapper);
        }
      }
    }

    dispatchQueuedEvents();
  }

  /**
   * Queue the {@code event} for dispatch during {@link #dispatchQueuedEvents()}. Events are queued in-order of
   * occurrence so they can be dispatched in the same order.
   */
  protected void enqueueEvent(Object event, EventHandler handler) {
    eventsToDispatch.get().offer(new EventWithHandler(event, handler));
  }

  /**
   * Drain the queue of events to be dispatched. As the queue is being drained, new events may be posted to the end of
   * the queue.
   */
  protected void dispatchQueuedEvents() {
    // don't dispatch if we're already dispatching, that would allow reentrancy and out-of-order events. Instead, leave
    // the events to be dispatched after the in-progress dispatch is complete.
    if (isDispatching.get()) {
      return;
    }

    isDispatching.set(true);
    try {
      while (true) {
        EventWithHandler eventWithHandler = eventsToDispatch.get().poll();
        if (eventWithHandler == null) {
          break;
        }

        if (eventWithHandler.handler.isValid()) {
          dispatch(eventWithHandler.event, eventWithHandler.handler);
        }
      }
    } finally {
      isDispatching.set(false);
    }
  }

  /**
   * Dispatches {@code event} to the handler in {@code wrapper}.  This method is an appropriate override point for
   * subclasses that wish to make event delivery asynchronous.
   *
   * @param event event to dispatch.
   * @param wrapper wrapper that will call the handler.
   */
  protected void dispatch(Object event, EventHandler wrapper) {
    try {
      wrapper.handleEvent(event);
    } catch (InvocationTargetException e) {
      throwRuntimeException( "Could not dispatch event: " + event.getClass() + " to handler " + wrapper, e);
    }
  }

  /**
   * Retrieves a mutable set of the currently registered handlers for {@code type}.  If no handlers are currently
   * registered for {@code type}, this method may either return {@code null} or an empty set.
   *
   * @param type type of handlers to retrieve.
   * @return currently registered handlers, or {@code null}.
   */
  Set<EventHandler> getHandlersForEventType(String type) {
    return handlersByType.get(type);
  }

  /**
   * Flattens a class's type hierarchy into a set of Class objects.  The set will include all superclasses
   * (transitively), and all interfaces implemented by these superclasses.
   *
   * @param concreteClass class whose type hierarchy will be retrieved.
   * @return {@code concreteClass}'s complete type hierarchy, flattened and uniqued.
   */
  Set<Class<?>> flattenHierarchy(Class<?> concreteClass) {
    Set<Class<?>> classes = flattenHierarchyCache.get(concreteClass);
    if (classes == null) {
      classes = getClassesFor(concreteClass);
      flattenHierarchyCache.put(concreteClass, classes);
    }

    return classes;
  }

  private Set<Class<?>> getClassesFor(Class<?> concreteClass) {
    List<Class<?>> parents = new LinkedList<Class<?>>();
    Set<Class<?>> classes = new HashSet<Class<?>>();

    parents.add(concreteClass);

    while (!parents.isEmpty()) {
      Class<?> clazz = parents.remove(0);
      classes.add(clazz);

      Class<?> parent = clazz.getSuperclass();
      if (parent != null) {
        parents.add(parent);
      }
    }
    return classes;
  }

  /**
   * Throw a {@link RuntimeException} with given message and cause lifted from an {@link
   * InvocationTargetException}. If the specified {@link InvocationTargetException} does not have a
   * cause, neither will the {@link RuntimeException}.
   */
  private static void throwRuntimeException(String msg, InvocationTargetException e) {
    Throwable cause = e.getCause();
    if (cause != null) {
      throw new RuntimeException(msg + ": " + cause.getMessage(), cause);
    } else {
      throw new RuntimeException(msg + ": " + e.getMessage(), e);
    }
  }

  private final Map<Class<?>, Set<Class<?>>> flattenHierarchyCache =
      new HashMap<Class<?>, Set<Class<?>>>();

  /** Simple struct representing an event and its handler. */
  static class EventWithHandler {
    final Object event;
    final EventHandler handler;

    public EventWithHandler(Object event, EventHandler handler) {
      this.event = event;
      this.handler = handler;
    }
  }
}
