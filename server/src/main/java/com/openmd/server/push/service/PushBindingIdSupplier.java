package com.openmd.server.push.service;

import java.util.UUID;

@FunctionalInterface
public interface PushBindingIdSupplier {
  UUID next();
}
