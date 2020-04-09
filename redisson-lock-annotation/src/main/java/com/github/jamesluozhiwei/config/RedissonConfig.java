package com.github.jamesluozhiwei.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisOperations;

/**
 * redisson实例配置(若自行配置则去除该配置)
 * @author jamesluozhiwei
 * @date 2020/4/9 19:20
 */
@Configuration
@ConditionalOnClass({RedissonClient.class})
public class RedissonConfig {


    @Value("${spring.redis.host:127.0.0.1}")
    private String redisHost;
    @Value("${spring.redis.port:6379}")
    private String redisPort;
    @Value("${spring.redis.password}")
    private String redisPassword;

    /**
     * 创建实例
     * @return
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = {"redissonClient"})
    public RedissonClient redissonClient(){
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress("redis://" + redisHost + ":" + redisPort);
        singleServerConfig.setPassword(redisPassword);
        return  Redisson.create(config);
    }

}
