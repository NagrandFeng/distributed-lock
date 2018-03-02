package com.zk.exception;

/**
 * @author Yeshufeng
 * @title
 * @date 2018/2/28
 */
public class LockException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public LockException(String e){
        super(e);
    }
    public LockException(Exception e){
        super(e);
    }
}
