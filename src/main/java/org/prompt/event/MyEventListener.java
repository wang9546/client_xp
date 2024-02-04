package org.prompt.event;

import java.util.EventListener;
public interface MyEventListener extends EventListener {
    void onEvent(MyEvent event);
}
