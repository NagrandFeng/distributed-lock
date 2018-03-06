# 分布式锁

## 概述

主要思想是，用一个状态值表示锁，对锁的占用和释放通过状态值来标识

## 数据库实现
//TODO...

## redis实现

可靠性

 	SET_IF_NOT_EXIST,当key不存在时，才进行创建，返回true；满足互斥性
  
 	设置过期时间，即时持有锁的客户端发生崩溃，依然保证不发生死锁
  
 	为key设置value，每个客户端唯一，防止客户端A误删了客户端B持有的锁


## zookeeper实现

和redis相比，多个客户端取锁时，取的顺序可以基于znode的时序临时节点实现，实现起来更方便

//TODO...
