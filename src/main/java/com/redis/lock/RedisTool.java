package com.redis.lock;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author Yeshufeng
 * @title
 * @date 2017/1/28
 */
public class RedisTool {
    private static final String LOCK_SUCCESS = "OK";
    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "PX";

    private static final Long RELEASE_SUCCESS = 1L;

    private static final String lockName = "my_lock";

    private static int count = 0;

    private static AtomicInteger countor = new AtomicInteger(0);

    private static CountDownLatch latch;

    private static final String redisHost = "127.0.0.1";

    private static final int port = 6379;

    private static JedisPoolConfig config;

    private static JedisPool pool;

    private static ExecutorService service;

    private static long defaultWaitTime = 3000;

    /**
     * 尝试获取分布式锁
     *
     * @param jedis      Redis客户端
     * @param lockKey    锁
     * @param requestId  请求标识
     * @param expireTime 超期时间
     * @return 是否获取成功
     */
    public static boolean tryGetDistributedLock(Jedis jedis, String lockKey, String requestId, int expireTime) {

        /**
         * lockKey，使用key来当锁
         * requestId，分布式锁要满足保证可靠性，通过给value赋值为requestId，可以知道这把锁是哪个请求加的了，
         *      在解锁的时候就可以有依据。requestId可以使用UUID.randomUUID().toString()方法生成。
         *      主要为了防止场景出现：客户端A释放了客户端B加的锁
         * SET_IF_NOT_EXIST，当key不存在时，进行set操作；若key已经存在，则不做任何操作；
         * SET_WITH_EXPIRE_TIME，这个参数我们传的是PX，意思是我们要给这个key加一个过期的设置，具体时间由第五个参数决定。
         * expireTime，key的过期时间。
         */
        String result = jedis.set(lockKey, requestId, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);

        if (LOCK_SUCCESS.equals(result)) {
            return true;
        }
        return false;

    }

    public static boolean releaseLock(Jedis jedis, String lockKey, String requestId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));

        if (RELEASE_SUCCESS.equals(result)) {
            return true;
        }
        return false;
    }

    static {
        config = new JedisPoolConfig();
        config.setMaxIdle(10);
        config.setMaxWaitMillis(2000);
        config.setMaxTotal(30);
        pool = new JedisPool(config, redisHost, port);
        //CountDownLatch保证主线程在全部线程结束之后退出
        latch = new CountDownLatch(3);
    }

    public static void main(String[] args) {
        String prefix = "Thread-";
        service = Executors.newFixedThreadPool(10);
        SubAddThread t1 = new SubAddThread(prefix + "A");
        SubAddThread t2 = new SubAddThread(prefix + "B");
        SubAddThread t3 = new SubAddThread(prefix + "C");
        service.submit(t1);
        service.submit(t2);
        service.submit(t3);


        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(count);
        System.out.println(countor.get());

    }


    static class SubAddThread implements Runnable {

        private String name;

        private Jedis jedis;

        public SubAddThread(String name) {
            this.name = name;
        }

        public void run() {
            jedis = pool.getResource();
            System.out.println(name + " starting...");
            for (int i = 1; i <= 100; i++) {
                String uuid = UUID.randomUUID().toString();
                try {
                    //fix...没有获取则等待
                    boolean isLock = tryGetDistributedLock(jedis, lockName, uuid, 1000);
                    long inLoopTime = System.currentTimeMillis();
                    while (!isLock) {
                        if (System.currentTimeMillis() > inLoopTime + defaultWaitTime) {
                            System.out.println("获取锁超时...");
                            break;
                        }
                        System.out.println(name + "尝试重新获取锁...");
                        isLock = tryGetDistributedLock(jedis, lockName, uuid, 1000);
                    }

                    if (isLock) {
                        System.out.println(name + " 第" + i + "次 " + "get lock success");
                        addKeyCount(jedis);
                    } else {
                        System.out.println(name + " 第" + i + "次 " + "get lock fail");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (releaseLock(jedis, lockName, uuid)) {
                        System.out.println(name + " 第" + i + "次 " + "release lock success");
                    } else {
                        System.out.println(name + " 第" + i + "次 " + "release lock fail");
                    }
                }

            }
            latch.countDown();
            System.out.println(name + " completed");
        }

    }

    /**
     * 模拟的需要竞争的资源
     *
     * @param jedis
     */
    private static void addKeyCount(Jedis jedis) {
        String value = jedis.get("count");
        if (value == null) {
            jedis.set("count", "0");
            value = jedis.get("count");
        }
        Integer count = Integer.parseInt(value);
        count++;
        jedis.set("count", String.valueOf(count));
    }

}





