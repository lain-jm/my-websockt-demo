package xyz.illya.mywebsocketdemo.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.LongAdder;

/**
 * @ClassName MyWebSocket
 * @Description TODO
 * @Author ChenJianming
 * @Version 1.0
 **/
@ServerEndpoint(value = "/websocket/{id}")
@Component
public class MyWebSocket {

    //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
    private static LongAdder onlineCount = new LongAdder();

    //concurrent包的线程安全Map，用来存放每个客户端对应的MyWebSocket对象。
    private static ConcurrentHashMap<String, MyWebSocket> webSocketMap = new ConcurrentHashMap<>();

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;

    private static Logger log = LoggerFactory.getLogger("webSocketLog");

    private String id=null;

    /**
     * 连接建立成功调用的方法*/
    @OnOpen
    public void onOpen(@PathParam(value = "id") String id, Session session) {
        this.session = session;
        this.id = id;
        webSocketMap.put(id,this);     //加入set中
        addOnlineCount();           //在线数加1
        log.info("有新连接加入！当前在线人数为" + getOnlineCount());
        try {
            sendMessage("有新连接加入！当前在线人数为" + getOnlineCount());
        } catch (IOException e) {
            System.out.println("IO异常");
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        //从Map中删除
        webSocketMap.remove(getId());
        subOnlineCount();           //在线数减1
        log.info("有一连接关闭！当前在线人数为" + getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息*/
    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("来自客户端的消息:" + message);

        //群发消息
        for (MyWebSocket item : webSocketMap.values()) {
            new Thread(() -> {
                try {
                    item.sendMessage("群发："+message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        //单发
//        try {
//            sendMessage("单独发："+message);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //定向发
//        try {
//            webSocketMap.get(getId()).sendMessage("指定用户："+getId()+" 发送");
//        }catch (IOException e){
//            e.printStackTrace();
//        }

    }

    /**
     * 发生错误时调用
     * */
     @OnError
     public void onError(Session session, Throwable error) {
         log.info("发生错误");
         error.printStackTrace();
     }

    /**
     *同个session同时发送会阻塞 抛出异常
     * @param message
     * @throws IOException
     */
    public void sendMessage(String message) throws IOException {
        synchronized (session){
            this.session.getBasicRemote().sendText(message);
        }
        //异步
//        this.session.getAsyncRemote().sendText(message);
    }


    public void sendMessageReturnResult(String message) throws IOException {
        session.getAsyncRemote().sendText(message, new SendHandler() {
            //服务器向客户端发送数据完毕之后，则调用SendHandler接口的onResult方法
            @Override
            public void onResult(SendResult result) {
                if(result.isOK()){
                    log.info(System.currentTimeMillis()+"");
                    System.out.println("信息发送完毕");
                }
            }
        });
    }

    /**
     * 群发自定义消息
     * */
    public static void sendInfo(String message) throws IOException {
        for (MyWebSocket item : webSocketMap.values()) {
            try {
                item.sendMessage(message);
            } catch (IOException e) {
                continue;
            }
        }
    }

    public static int getOnlineCount() {
        return onlineCount.intValue();
    }

    public static void addOnlineCount() {
        MyWebSocket.onlineCount.increment();
    }

    public static void subOnlineCount() {
        MyWebSocket.onlineCount.decrement();
    }

    public String getId(){
        return this.id;
    }
}
