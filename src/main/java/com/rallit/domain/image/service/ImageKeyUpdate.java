package com.rallit.domain.image.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ImageKeyUpdate implements TransactionSynchronization {
  private String previous;
  private boolean replaced;
  // Until a transaction starts, the newly uploaded image is unreferenced.
  private int completionStatus = STATUS_ROLLED_BACK;

  public void register() {
    TransactionSynchronizationManager.registerSynchronization(this);
    completionStatus = STATUS_UNKNOWN;
  }

  public void replaced(String previous) {
    this.previous = previous;
    this.replaced = true;
  }

  @Override
  public void afterCompletion(int status) {
    completionStatus = status;
  }

  public String unreferencedKey(String uploaded) {
    return switch (completionStatus) {
      case STATUS_COMMITTED -> replaced ? previous : uploaded;
      case STATUS_ROLLED_BACK -> uploaded;
      default -> null;
    };
  }
}
