package com.farmacia.util;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Cierra el hilo auxiliar de Connector/J cuando Tomcat detiene esta aplicación web. */
@WebListener
public class LimpiezaConectorMySQL implements ServletContextListener {

    /** Detiene de forma segura el hilo asociado al cargador de clases de esta aplicación. */
    @Override
    public void contextDestroyed(ServletContextEvent evento) {
        AbandonedConnectionCleanupThread.checkedShutdown();
    }
}
