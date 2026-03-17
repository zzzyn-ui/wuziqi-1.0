import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class test_redis {
    public static void main(String[] args) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setTestOnBorrow(true);
        
        JedisPool pool = new JedisPool(config, "localhost", 6379, 5000, "redis123", 0);
        
        try (Jedis jedis = pool.getResource()) {
            System.out.println("Connected to Redis");
            
            // Test SADD
            long result = jedis.sadd("match:queue:casual", "999", "888");
            System.out.println("SADD result: " + result);
            
            // Test SCARD
            long count = jedis.scard("match:queue:casual");
            System.out.println("SCARD count: " + count);
            
            // Test SMEMBERS
            java.util.Set<String> members = jedis.smembers("match:queue:casual");
            System.out.println("SMEMBERS: " + members);
        }
        
        pool.close();
    }
}
