package org.prompt.event;

import org.prompt.client.EventResp;

import java.util.ArrayList;
import java.util.List;
public class MyEventSource {
    private List<MyEventListener> listeners = new ArrayList<>();
    public void addEventListener(MyEventListener listener) {
        listeners.add(listener);
    }
    public void fireEvent(MyEvent event) {
        for (MyEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
    // 方法来生成和发送事件
    public void someOperationThatGeneratesEvents(EventResp message) {
        // ... 执行一些操作 ...
        MyEvent event = new MyEvent(this, message);
        fireEvent(event);
    }
}