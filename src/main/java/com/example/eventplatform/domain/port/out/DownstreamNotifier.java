package com.example.eventplatform.domain.port.out;

import com.example.eventplatform.domain.model.Event;

/**
 * Integração com sistemas externos. Executada fora do caminho crítico de ingestão,
 * a partir do tópico de eventos processados, para que lentidão externa nunca afete a API.
 */
public interface DownstreamNotifier {

    void notify(Event event);
}
