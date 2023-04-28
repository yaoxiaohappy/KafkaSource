/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.kafka.common.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A simple read-optimized map implementation that synchronizes only writes and does a full copy on each modification
 * 这个数据结构在高并发的情况下是线程安全的
 * 采用读写分离的思想，每次插入数据的时候都开辟新的内存空间，所以会有个小缺点，就是每次插入数据的时候会耗费内存空间
 * 这样的数据结构适合写少读多的场景
 *
 * 对于bacthes来说它面对的场景就是读多写少
 * batches：
 * 读数据：每生产一条消息，都会从batches读取数据
 * 假如没秒中生产10万次消息，就要读10万次
 * 写数据：当要batches当中队列不存在，才会新插入数据，假设有100个分区，一共才插入100次数据
 */
public class CopyOnWriteMap<K, V> implements ConcurrentMap<K, V> {

    /**
     * 核心是这个map，它有个特点，volatile 的好处是多线程的情况下，这个是线程安全的
     */
    private volatile Map<K, V> map;

    public CopyOnWriteMap() {
        this.map = Collections.emptyMap();
    }

    public CopyOnWriteMap(Map<K, V> map) {
        this.map = Collections.unmodifiableMap(map);
    }

    @Override
    public boolean containsKey(Object k) {
        return map.containsKey(k);
    }

    @Override
    public boolean containsValue(Object v) {
        return map.containsValue(v);
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return map.entrySet();
    }
    /*
    没有加锁，增加的性能，因为写操作读写分离，所以也是线程安全的
     */
    @Override
    public V get(Object k) {
        return map.get(k);
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public synchronized void clear() {
        this.map = Collections.emptyMap();
    }

    /*synchronized 关键字修饰，说明该方法是线程安全的
    //该方法中仅仅做纯内存的操作，即使加了锁，这段代码的性能也依然OK
    这中设计方式采用的是读写分离的设计思想，所以我们读数据的操作是天然线程安全的

     */
    @Override
    public synchronized V put(K k, V v) {
        //新开辟内存空间，保证读写分离
        Map<K, V> copy = new HashMap<K, V>(this.map);
        //插入数据
        V prev = copy.put(k, v);
        //这个map是用volatial修饰的，所以这个map发生变化的时候，读线程是可以感知到的
        this.map = Collections.unmodifiableMap(copy);
        return prev;
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> entries) {
        Map<K, V> copy = new HashMap<K, V>(this.map);
        copy.putAll(entries);
        this.map = Collections.unmodifiableMap(copy);
    }

    @Override
    public synchronized V remove(Object key) {
        Map<K, V> copy = new HashMap<K, V>(this.map);
        V prev = copy.remove(key);
        this.map = Collections.unmodifiableMap(copy);
        return prev;
    }

    /**
     *
     * @param k
     * @param v
     * 如果key不存在，就put。否则返回get
     * @return
     */
    @Override
    public synchronized V putIfAbsent(K k, V v) {
        if (!containsKey(k))
            return put(k, v);
        else
            return get(k);
    }

    @Override
    public synchronized boolean remove(Object k, Object v) {
        if (containsKey(k) && get(k).equals(v)) {
            remove(k);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean replace(K k, V original, V replacement) {
        if (containsKey(k) && get(k).equals(original)) {
            put(k, replacement);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized V replace(K k, V v) {
        if (containsKey(k)) {
            return put(k, v);
        } else {
            return null;
        }
    }

}
