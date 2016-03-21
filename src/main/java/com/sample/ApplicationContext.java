package com.sample;

/**
 * Created by yotamm on 20/02/16.
 */
public interface ApplicationContext {
    <T> T getBean(Class<T> type);
}
