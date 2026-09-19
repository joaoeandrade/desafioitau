package com.example.eventplatform.adapter.out.integration;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.port.out.DownstreamNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Notificador padrão quando nenhuma integração externa está configurada. */
@Component
@ConditionalOnProperty(name = "platform.integration.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingDownstreamNotifier implements DownstreamNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingDownstreamNotifier.class);

    @Override
    public void notify(Event event) {
        log.debug("integração desabilitada; evento {} ({}) não enviado", event.id(), event.type());
    }
}
