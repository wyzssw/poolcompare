package com.justdebugit.pool.compare;

/**
 * @author Daniel
 */
public class Benchmark {

    public static void main(String[] args) throws Exception {

        System.out.println("-----------warm up------------");
        new BenchmarkFastObjectPool(50,  1000);
        new BenchmarkCommons(50,  1000);

        System.out.println("-----------fast object pool------------");
        new BenchmarkFastObjectPool(50,  5000);
        new BenchmarkFastObjectPool(100, 5000);
        new BenchmarkFastObjectPool(150, 5000);
        new BenchmarkFastObjectPool(200, 3000);
        new BenchmarkFastObjectPool(250, 3000);
        new BenchmarkFastObjectPool(300, 3000);
        new BenchmarkFastObjectPool(350, 2000);
        new BenchmarkFastObjectPool(400, 2000);
        new BenchmarkFastObjectPool(450, 2000);
        new BenchmarkFastObjectPool(500, 1000);
        new BenchmarkFastObjectPool(550, 1000);
        new BenchmarkFastObjectPool(600, 1000);

        System.out.println("------------Apache commons pool-----------");
        // too slow, so less loops
        new BenchmarkCommons(50, 5000);
        new BenchmarkCommons(100, 5000);
        new BenchmarkCommons(150, 5000);
        new BenchmarkCommons(200, 3000);
        new BenchmarkCommons(250, 3000);
        new BenchmarkCommons(300, 3000);
        new BenchmarkCommons(350, 2000);
        new BenchmarkCommons(400, 2000);
        new BenchmarkCommons(450, 2000);
        new BenchmarkCommons(500, 1000);
        new BenchmarkCommons(550, 1000);
        new BenchmarkCommons(600, 1000);

    }
}
