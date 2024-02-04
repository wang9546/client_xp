package org.prompt.event;
import org.prompt.client.EventResp;

import java.util.EventObject;
public class MyEvent extends EventObject {
    private EventResp message;
    public MyEvent(Object source, EventResp message) {
        super(source);
        this.message = message;
    }
    public EventResp getMessage() {
        return message;
    }
}