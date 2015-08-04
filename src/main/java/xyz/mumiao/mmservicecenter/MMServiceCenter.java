package xyz.mumiao.mmservicecenter;

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

    private MMServiceCenter()
    {
        log("MMServiceCenter init");
        lock = new ReentrantLock();
        hashMapService = new HashMap<>();
    }

    public static MMServiceCenter init()
    {
        if (defaultServiceCenter == null)
            defaultServiceCenter = new MMServiceCenter();
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
                obj.onServiceInit();
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

        obj.state.isServiceRemoved = true;

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
            if (!service.state.isServicePersistent)
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
