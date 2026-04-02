package com.gateway.workers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("worker") // This ensures it ONLY runs in the worker container
public class WorkerRunner implements CommandLineRunner {

    @Autowired private PaymentWorker paymentWorker;
    @Autowired private RefundWorker refundWorker;
    @Autowired private WebhookWorker webhookWorker;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 Starting Background Workers...");
        paymentWorker.start();
        refundWorker.start();
        webhookWorker.start();
    }
}