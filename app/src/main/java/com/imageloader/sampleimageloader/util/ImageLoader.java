package com.imageloader.sampleimageloader.util;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

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
                        //线程池去取出一个任务进行执行
                        mThreadPool.execute(getTask());
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

    /**
     * 任务队列去出一个方法
     * @return
     */
    private Runnable getTask() {
        if (mType==Type.FIFO){
            return mTaskQueue.removeFirst();
        }else if (mType==Type.LIFO){
            return mTaskQueue.removeLast();
        }
        return null;
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

    public void loadImage(final String path, final ImageView imageView){
        imageView.setTag(path);
        if (mUIHandler==null){
            mUIHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    //获取图片，为imageView回调设置图片
                    ImageBeanHolder holder = (ImageBeanHolder) msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imageView = holder.imageView;
                    String path = holder.path;
                    if (imageView.getTag().toString().equals(path)){
                        imageView.setImageBitmap(bm);
                    }
                }
            };
        }
        //LruCache 查找 找到返回 找不到放入Task队列 且发送通知去体现后台轮询线程
        Bitmap bm = getBitmapFromLruCache(path);
        if (bm!=null){
            Message message = Message.obtain();
            ImageBeanHolder holder = new ImageBeanHolder();
            holder.bitmap = bm;
            holder.path = path;
            holder.imageView = imageView;
            message.obj = holder;
            mUIHandler.sendMessage(message);
        }else {
            addTasks(new Runnable() {
                @Override
                public void run() {
                   //加载图片
                   //图片压缩
                    //1.获取图片需要显示的大小
                    ImageSize imageSize = getImageViewSize(imageView);
                    //2.压缩图片
                    Bitmap bm = decodeSampledBitmapFromPath(path,imageSize.with,imageSize.height);
                }
            });
        }
    }

    /**
     * 根据图片需要显示的宽和高压缩
     * @param path
     * @param with
     * @param height
     * @return
     */
    private Bitmap decodeSampledBitmapFromPath(String path, int with, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = caculateInSampleSize(options,with,height);
        //inSampleSize再次解析
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        return bitmap;
    }

    private int caculateInSampleSize(BitmapFactory.Options options, int reqWith, int reqHeight) {
        int with = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if (with>reqHeight||height>reqHeight){
            int widthRound = Math.round(with * 1.0f / reqWith);
            int heightRound = Math.round(height * 1.0f / reqHeight);
            inSampleSize = Math.max(widthRound,heightRound);
        }
        return inSampleSize;
    }

    /**
     * 根据imageView获取适当压缩宽和高
     * @param imageView
     * @return
     */
    @SuppressLint("NewApi")
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        int width = imageView.getWidth();//获取imageView实际宽度
        if (width<=0){
            width= lp.width;//获取imageView在layout中声名的宽度
        }
        if (width<=0){
           width = imageView.getMaxWidth();//检查最大值
        }
        if (width<=0){
           width =displayMetrics.widthPixels;
        }

        int height = imageView.getHeight();//获取imageView实际宽度
        if (height<=0){
            height= lp.height;//获取imageView在layout中声名的宽度
        }
        if (height<=0){
            height = imageView.getMaxHeight();//检查最大值
        }
        if (height<=0){
            height =displayMetrics.heightPixels;
        }
        imageSize.with = width;
        imageSize.height = height;
        return imageSize;
    }

    private void addTasks(Runnable runnable) {
        mTaskQueue.add(runnable);
        mPoolThreadHandler.sendEmptyMessage(0);
    }

    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }
    private class ImageSize{
        int with;
        int height;
    }
    private class ImageBeanHolder{
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
