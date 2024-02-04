package org.prompt.event;

public class MyEventListenerImpl implements MyEventListener {
    @Override
    public void onEvent(MyEvent event) {
        System.out.print(event.getMessage().getChoices().get(0).getDelta().get(0).getContent());
    }
}