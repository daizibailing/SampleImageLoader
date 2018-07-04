package com.imageloader.sampleimageloader.util;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.LruCache;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by zhangke on 2018/7/4 0004 13:41
 * E-Mail Address：2426017569@qq.com
 */
public class ImageLoader {
    private static ImageLoader mInstance;
    private LruCache<String,Bitmap> mLruCache;
    private ExecutorService mThreadPool;
    private static final int DEAFULT_THREAD_COUNT=1;
    /**
     * 队列的调度方式
     */
    private Type mType = Type.LIFO;
    private LinkedList<Runnable> mTaskQueue;
    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    /**
     * UI线程中的Handler
     */
    private Handler mUIHandler;

    /**
     * 　FIFO: 全称First in, First out，先进先出。
     　　LIFO: 全称Last in, First out，后进先出。
     */
    public enum  Type{
        FIFO,LIFO;
    }
    private ImageLoader(int threadCount,Type type){
        init(threadCount,type);
    }

    private void init(int threadCount, Type type) {
        //后台轮询线程
        mPoolThread= new Thread(){
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);

                    }
                };
                Looper.loop();
            }
        };
        mPoolThread.start();
        //应用最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;
        mLruCache = new LruCache<String,Bitmap>(cacheMemory){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight();
            }
        };
        //创建一个定长线程池，可控制线程最大并发数，超出的线程会在队列中等待。
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;

    }

    public static ImageLoader getmInstance(){
        if (mInstance==null){
            synchronized (ImageLoader.class){
                if (mInstance==null){
                    mInstance = new ImageLoader(DEAFULT_THREAD_COUNT,Type.LIFO);
                }
            }
        }
        return mInstance;
    }
}
