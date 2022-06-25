package com.acow.netty.rpc.api.impl;

import com.acow.netty.rpc.api.IRpcHelloService;

public class RpcHelloServiceImpl implements IRpcHelloService {
    @Override
    public String hello(String name) {
        return "Hello "+name+"!";
    }
}
