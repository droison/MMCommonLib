package xyz.mumiao.mmservicecenter;

import android.content.Context;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import android.util.Log;

/**
 * Created by song on 15/6/14.
 */
public class MMServiceCenter {
    private static MMServiceCenter defaultServiceCenter;
    private static boolean isDebugMode = true;
    private final ReentrantLock lock;
    private HashMap<String, MMServiceInterface> hashMapService;
    private Context context;

    private MMServiceCenter()
    {
        log("MMServiceCenter init");
        lock = new ReentrantLock();
        hashMapService = new HashMap<>();
    }

    public static MMServiceCenter init(Context context)
    {
        if (defaultServiceCenter == null)
        {
            defaultServiceCenter = new MMServiceCenter();
            defaultServiceCenter.context = context;
        }
        return defaultServiceCenter;
    }

    public static void configDebug(boolean isDebug)
    {
        isDebugMode = isDebug;
    }

    @Nullable
    public static MMServiceCenter defaultServiceCenter()
    {
        return defaultServiceCenter;
    }

    public static <T extends MMServiceInterface> T getService(Class<T> cls)
    {
        defaultServiceCenter.lock.lock();

        T obj = (T) defaultServiceCenter.hashMapService.get(cls.getName());
        if (obj == null)
        {
            try {
                obj = cls.newInstance();
                log("getService:Create service object:" + obj);
            } catch (InstantiationException e) {
                log("getService:cls.newInstance()", e);
            } catch (IllegalAccessException e)
            {
                log("getService:cls.newInstance()", e);
            }

            if (obj != null)
            {
                defaultServiceCenter.hashMapService.put(cls.getName(), obj);
                obj.onServiceInit(defaultServiceCenter.context);
            }
            else
            {
                defaultServiceCenter.lock.unlock();
            }

        }
        else
        {
            defaultServiceCenter.lock.unlock();
        }

        return obj;
    }

    /**
     * 除了没有返回值，和上面的getService效果基本一致，用于初始化
     * @param cls
     *        需要初始化的Service类
     * @param context
     *        初始化的Service时传入的context，如果为空会传入ServiceCenter默认的Context
     * @param <T>
     *        泛型，限制cls需要implements MMServiceInterface
     */
    public static <T extends MMServiceInterface> void callInitService(Class<T> cls, Context context)
    {
        defaultServiceCenter.lock.lock();

        T obj = (T) defaultServiceCenter.hashMapService.get(cls.getName());
        if (obj == null)
        {
            try {
                obj = cls.newInstance();
                log("getService:Create service object:" + obj);
            } catch (InstantiationException e) {
                log("getService:cls.newInstance()", e);
            } catch (IllegalAccessException e)
            {
                log("getService:cls.newInstance()", e);
            }
            if (obj != null)
            {
                defaultServiceCenter.hashMapService.put(cls.getName(), obj);
                obj.onServiceInit(context != null? context: defaultServiceCenter.context);
            }
        }
        defaultServiceCenter.lock.unlock();
    }

    /**
     * 在MMServiceCenter中移除对该Service的持有
     * @param cls
     *        需要remove的Service类
     * @param <T>
     *        泛型，限制cls需要implements MMServiceInterface
     */
    public static <T extends MMServiceInterface> void removeService(Class<T> cls)
    {
        defaultServiceCenter.lock.lock();

        MMServiceInterface obj = defaultServiceCenter.hashMapService.get(cls.getName());

        if (obj == null)
        {
            defaultServiceCenter.lock.unlock();
            return ;
        }

        defaultServiceCenter.hashMapService.remove(cls.getName());

        obj.getServiceState().isServiceRemoved = true;

        defaultServiceCenter.lock.unlock();
    }

    public static void callEnterForeground()
    {
        defaultServiceCenter.lock.lock();
        Collection<MMServiceInterface> arrayCopy = defaultServiceCenter.hashMapService.values();
        defaultServiceCenter.lock.unlock();

        Iterator<MMServiceInterface> iterator = arrayCopy.iterator();

        while (iterator.hasNext())
        {
            MMServiceInterface service = iterator.next();
            service.onServiceEnterForeground();
        }
    }

    public static void callEnterBackground()
    {
        defaultServiceCenter.lock.lock();
        Collection<MMServiceInterface> arrayCopy = defaultServiceCenter.hashMapService.values();
        defaultServiceCenter.lock.unlock();

        Iterator<MMServiceInterface> iterator = arrayCopy.iterator();

        while (iterator.hasNext())
        {
            MMServiceInterface service = iterator.next();
            service.onServiceEnterBackground();
        }
    }

    public static void callTerminate()
    {
        defaultServiceCenter.lock.lock();
        Collection<MMServiceInterface> arrayCopy = defaultServiceCenter.hashMapService.values();
        defaultServiceCenter.lock.unlock();

        Iterator<MMServiceInterface> iterator = arrayCopy.iterator();

        while (iterator.hasNext())
        {
            MMServiceInterface service = iterator.next();
            service.onServiceTerminate();
        }
        defaultServiceCenter.hashMapService.clear();
    }

    public static void callReloadData()
    {
        defaultServiceCenter.lock.lock();
        Collection<MMServiceInterface> arrayCopy = defaultServiceCenter.hashMapService.values();
        defaultServiceCenter.lock.unlock();

        Iterator<MMServiceInterface> iterator = arrayCopy.iterator();

        while (iterator.hasNext())
        {
            MMServiceInterface service = iterator.next();
            service.onServiceReloadData();
        }
    }

    public static void callClearData()
    {
        defaultServiceCenter.lock.lock();
        Collection<MMServiceInterface> arrayCopy = defaultServiceCenter.hashMapService.values();
        defaultServiceCenter.lock.unlock();
        Iterator<MMServiceInterface> iterator = arrayCopy.iterator();

        List<Class<? extends MMServiceInterface>> classArrayList = new ArrayList<>();
        while (iterator.hasNext())
        {
            MMServiceInterface service = iterator.next();
            service.onServiceClearData();
            if (!service.getServiceState().isServicePersistent)
            {
                // remove
                classArrayList.add(service.getClass());
            }

        }
        for (Class<? extends MMServiceInterface> serviceClass : classArrayList)
        {
            removeService(serviceClass);
        }
    }

    public static void log(String msg)
    {
        if (isDebugMode)
        {
            Log.d("MMServiceCenter", msg);
        }
    }

    public static void log(String msg, Exception e)
    {
        if (e == null )
        {
           if (isDebugMode)Log.d("MMServiceCenter", msg);
        }
        else
        {
            Log.e("MMServiceCenter", msg, e);
        }
    }
}
