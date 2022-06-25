package com.acow.netty.rpc.consumer.proxy;

import com.acow.netty.rpc.provider.InvokerProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class RpcProxy {

    public static <T> T create(Class<?> clazz){
        //clazz传进来本身就是interface
        MethodProxy proxy=new MethodProxy(clazz);
        Class<?>[] interfaces=clazz.isInterface()?new Class[]{clazz}:clazz.getInterfaces();
        T result=(T) Proxy.newProxyInstance(clazz.getClassLoader(),interfaces,proxy);

        return result;
    }

    public static class MethodProxy implements InvocationHandler{
        private Class<?> clazz;

        public MethodProxy(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //如果传过来是一个已实现的具体类（本次演示略过此逻辑）
            if(Object.class.equals(method.getDeclaringClass())){
                try {
                    return method.invoke(this,args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //如果传过来的是一个接口
            }else {
                return rpcInvoke(proxy,method,args);
            }
            return null;
        }

        /**
         * 实现接口的核心方法
         *
         */
        public Object rpcInvoke(Object proxy,Method method,Object[] args) throws InterruptedException {
            //传输协议封装
            InvokerProtocol msg=new InvokerProtocol();
            msg.setClassName(this.clazz.getName());
            msg.setMethodName(method.getName());
            msg.setValues(args);
            msg.setParames(method.getParameterTypes());

            final RpcProxyHandler consumerHandler=new RpcProxyHandler();
            EventLoopGroup group=new NioEventLoopGroup();

            try {
                Bootstrap b=new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY,true)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline pipeline=ch.pipeline();
                                //自定义协议解码器
                                pipeline.addLast("frameDecoder",new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,0,4,0,4));
                                //自定义协议编码器
                                pipeline.addLast("frameEncoder",new LengthFieldPrepender(4));
                                //对象参数类型编码器
                                pipeline.addLast("encoder",new ObjectEncoder());
                                //对象参数类型解码器
                                pipeline.addLast("decoder",new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)));

                                pipeline.addLast("handler",consumerHandler);
                            }
                        });

                ChannelFuture future=b.connect("localhost",8080).sync();
                future.channel().writeAndFlush(msg).sync();
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
            return consumerHandler.getResponse();
        }
    }

}
