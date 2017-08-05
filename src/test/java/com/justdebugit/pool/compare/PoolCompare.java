package com.justdebugit.pool.compare;

import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import cn.danielw.fop.PoolConfig;
import redis.clients.jedis.Jedis;
import stormpot.Allocator;
import stormpot.BlazePool;
import stormpot.Config;
import stormpot.Poolable;
import stormpot.Slot;
import stormpot.Timeout;

public class PoolCompare {

  
  private static final ThreadLocal<Jedis> THREAD_LOCAL = new ThreadLocal<>();
  
  
  /**
   * commons-pool 
   * @return
   */
  public static ObjectPool<Jedis> getJedisCommonPool() {
    GenericObjectPoolConfig config =  new GenericObjectPoolConfig();
    config.setMaxIdle(5);
    config.setMaxTotal(5);
    config.setMinIdle(1);
    GenericObjectPool<Jedis> commonPool = new GenericObjectPool<Jedis>(
        new JedisFactory("127.0.0.1", 6379, 10000,
            10000, null, 0, null),config);

      return commonPool;
  }
  
  public static class MyPoolable implements Poolable {
    private final Slot slot;
    private final Jedis jedis;
    public MyPoolable(Slot slot) {
      this.slot = slot;
      jedis = new Jedis("127.0.0.1", 6379);
    }

    public Jedis getJedis(){
      return jedis;
    }
    public void release() {
      slot.release(this);
    }
  }
  
  public static class MyAllocator implements Allocator<MyPoolable> {
    public MyPoolable allocate(Slot slot) throws Exception {
      return new MyPoolable(slot);
    }

    public void deallocate(MyPoolable poolable) throws Exception {
    }
  }
  
  
  public static BlazePool<MyPoolable> getBlazePool() {
    Config<MyPoolable> config = new Config<MyPoolable>().setAllocator(new MyAllocator()).setSize(5);
    BlazePool<MyPoolable> pool = new BlazePool<MyPoolable>(config);
    return pool;
  }
  
  
  public static cn.danielw.fop.ObjectPool<Jedis> getFastObjPool(){
    PoolConfig config = new PoolConfig();
    config.setPartitionSize(4);
    config.setMaxSize(1);
    config.setMinSize(1);
    config.setMaxIdleMilliseconds(60 * 1000 * 5);
    
    cn.danielw.fop.ObjectFactory<Jedis> factory = new cn.danielw.fop.ObjectFactory<Jedis>() {
      @Override public Jedis create() {
          return new Jedis("127.0.0.1");
      }
      @Override public void destroy(Jedis o) {
      }
      @Override public boolean validate(Jedis o) {
          return true; 
      }
    };
    
    cn.danielw.fop.ObjectPool<Jedis> pool = new cn.danielw.fop.ObjectPool<Jedis>(config, factory);
    return pool;
  }
  
  
  
  interface PoolApi<T>{
      T get() throws Exception;
      void release(T t) throws Exception;
  }
  
  
  public static void main(String[] args) throws InterruptedException {
      final ObjectPool<Jedis> objectPool = getJedisCommonPool();
      final BlazePool<MyPoolable> blazePool = getBlazePool();
      ThreadLocal<MyPoolable> mLocal = new ThreadLocal<>();
      ThreadLocal<cn.danielw.fop.Poolable<Jedis>> aLocal = new ThreadLocal<>();
      final cn.danielw.fop.ObjectPool<Jedis> fstObjPool = getFastObjPool();
      System.out.println("Stormpot has consumed :");
      
      testConsumeTime(new PoolApi<Jedis>() {
        @Override
        public  Jedis get() throws NoSuchElementException,
                IllegalStateException, Exception {
          Timeout timeout = new Timeout(10000, TimeUnit.SECONDS);
            MyPoolable myPoolable = blazePool.claim(timeout);
            mLocal.set(myPoolable);
            return myPoolable.getJedis();
        }

        @Override
        public  void release(Jedis jedis) throws Exception {
          mLocal.get().release();
 
        }
      });
      
      System.out.println("fastobjectpool has consumed :");
      testConsumeTime(new PoolApi<Jedis>() {
        @Override
        public  Jedis get() throws NoSuchElementException,
                IllegalStateException, Exception {
          cn.danielw.fop.Poolable<Jedis> poolable = fstObjPool.borrowObject();
          aLocal.set(poolable);
          return poolable.getObject();
        }

        @Override
        public  void release(Jedis jedis) throws Exception {
          aLocal.get().returnObject();
          aLocal.remove();
        }
      });
      
      System.out.println("commons-pool has consumed :");
      testConsumeTime(new PoolApi<Jedis>() {

        @Override
        public  Jedis get() throws NoSuchElementException,
                IllegalStateException, Exception {
            return objectPool.borrowObject();
        }

        @Override
        public  void release(Jedis t) throws Exception {
            objectPool.returnObject(t);
        }
    });
      
      
      
      
      System.out.println("threadlocalpool has consumed :");
      testConsumeTime(new PoolApi<Jedis>() {

          @Override
          public synchronized Jedis get() throws InterruptedException {
              if (THREAD_LOCAL.get() == null) {
                THREAD_LOCAL.set(new Jedis());
              }
              return THREAD_LOCAL.get();
          }

          @Override
          public synchronized  void release(Jedis t) {
          }
      });
      
  }

  public static void testConsumeTime(final PoolApi<Jedis> poolApi)  {
      ExecutorService executorService = Executors.newFixedThreadPool(10);
      final AtomicLong countLong  = new AtomicLong(100008);
      final CountDownLatch latch = new CountDownLatch(1);
      final CountDownLatch shutdownLatch = new CountDownLatch(1);
      final AtomicLong atomicLong = new AtomicLong();
      
      for (int i = 0; i < 10; i++) {
          executorService.submit(new Runnable() {
              
              @Override
              public void run() {
                  try {
                      latch.await();
                  } catch (InterruptedException e1) {
                      e1.printStackTrace();
                  }
                  atomicLong.compareAndSet(0, System.currentTimeMillis());
                  AtomicLong start = new AtomicLong(System.currentTimeMillis());
                  Long llLong = 0L;
                  while (true) {
                    Jedis jedis = null;
                      try {
                          start.set(System.currentTimeMillis());
                          jedis = poolApi.get();
                          start.set(System.currentTimeMillis() - start.get());
                          llLong = llLong + start.get();
                          jedis.get("a");
//                          jedis.set("a", "a");
                          if (countLong.decrementAndGet()==100) {
                              System.out.println("总消耗" + (System.currentTimeMillis()-atomicLong.get()) +" ms");
                              System.out.println("borrow消耗:" + llLong+" ms");
                              shutdownLatch.countDown();
                              break;
                          };
                          if (countLong.get()<=100) {
                              break;
                          }
                      } catch (InterruptedException e) {
                          //ignore
                      }catch (Exception e) {
                          e.printStackTrace();
                      }
                      finally{
                          if (jedis!=null) {
                              try {
                                  poolApi.release(jedis);
                              } catch (Exception e) {
                                  e.printStackTrace();
                              }
                          }
                      }
                  }
                  
              }
          });
      }
      latch.countDown();
      try {
          shutdownLatch.await();
      } catch (InterruptedException e) {
          e.printStackTrace();
      }
      executorService.shutdownNow();
  }





}
