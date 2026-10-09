package com.eventpass.ms_ordenes.messaging;

import com.eventpass.ms_ordenes.model.Orden;

/**
 * Punto de enlace con la cola de "ordenes pendientes de emision" (Amazon SQS en el diagrama).
 * Por ahora solo hay una implementacion que escribe en el log; despues se reemplaza
 * por una que envie el mensaje a SQS (o a LocalStack si se prueba en Docker).
 */
public interface OrdenPublisher {

    void publicar(Orden orden);
}
