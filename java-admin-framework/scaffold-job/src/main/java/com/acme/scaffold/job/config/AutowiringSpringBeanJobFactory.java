package com.acme.scaffold.job.config;

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 让 Quartz 创建的 Job 实例也能获得 Spring 依赖注入。
 *
 * <p>直接实现 Quartz 原生 {@link JobFactory}：按 JobDetail 的类反射实例化，
 * 再交给 {@link AutowireCapableBeanFactory#autowireBean(Object)} 注入 {@code @Autowired} 字段。
 * 相比 {@code SpringBeanJobFactory} 少引入 {@code spring-context-support} 依赖，
 * 且无需把 Job 类注册成 {@code @Component} 被容器提前实例化。
 */
public class AutowiringSpringBeanJobFactory implements JobFactory, ApplicationContextAware {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
        Class<? extends Job> jobClass = bundle.getJobDetail().getJobClass();
        try {
            Job job = jobClass.getDeclaredConstructor().newInstance();
            if (beanFactory != null) {
                beanFactory.autowireBean(job);
            }
            return job;
        } catch (ReflectiveOperationException e) {
            throw new SchedulerException("创建 Quartz Job 实例失败: " + jobClass.getName(), e);
        }
    }
}
