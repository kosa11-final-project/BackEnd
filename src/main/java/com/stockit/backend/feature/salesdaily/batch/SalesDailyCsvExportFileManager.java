package com.stockit.backend.feature.salesdaily.batch;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class SalesDailyCsvExportFileManager implements JobExecutionListener {

    public static final String TEMPORARY_PATH_CONTEXT_KEY =
            "salesDailyCsvExportTemporaryPath";

    private final SalesDailyCsvExportProperties properties;

    public SalesDailyCsvExportFileManager(SalesDailyCsvExportProperties properties) {
        this.properties = properties;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        Path outputPath = properties.requiredOutputPath();
        Path temporaryPath = temporaryPath(jobExecution.getId());
        Path parent = outputPath.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(temporaryPath);
            jobExecution.getExecutionContext().putString(
                    TEMPORARY_PATH_CONTEXT_KEY,
                    temporaryPath.toString()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("SALES_DAILY CSV 임시 파일을 준비하지 못했습니다.", exception);
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Path temporaryPath = temporaryPath(jobExecution.getId());
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            deleteTemporaryFile(temporaryPath);
            return;
        }

        try {
            moveAtomically(temporaryPath, properties.requiredOutputPath());
        } catch (IOException exception) {
            deleteTemporaryFile(temporaryPath);
            throw new IllegalStateException("SALES_DAILY CSV 최종 파일을 확정하지 못했습니다.", exception);
        }
    }

    public Path temporaryPath(Long jobExecutionId) {
        Path outputPath = properties.requiredOutputPath();
        String temporaryFileName = outputPath.getFileName()
                + "." + jobExecutionId + ".tmp";
        return outputPath.resolveSibling(temporaryFileName);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporaryFile(Path temporaryPath) {
        try {
            Files.deleteIfExists(temporaryPath);
        } catch (IOException exception) {
            throw new IllegalStateException("실패한 SALES_DAILY CSV 임시 파일을 삭제하지 못했습니다.", exception);
        }
    }
}
