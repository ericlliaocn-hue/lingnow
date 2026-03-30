package cc.lingnow.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.util.List;

/**
 * Industrial Grade Excel Report Generator for LingNow Stress Test.
 * Supports embedding screenshots and Visual DNA analysis.
 */
public class ExcelReportGenerator {

    /**
     * Exports the gathered data to an Excel file.
     *
     * @param fileName the target file name
     * @param data     the list of report rows
     */
    public static void export(String fileName, List<IndustryReportRow> data) {
        EasyExcel.write(fileName, IndustryReportRow.class)
                .sheet("LingNow 3.5 行业压测报告")
                .doWrite(data);
    }

    @Data
    @Builder
    @ContentRowHeight(100)
    public static class IndustryReportRow {
        @ExcelProperty("行业意图")
        @ColumnWidth(30)
        private String userIntent;

        @ExcelProperty("视觉 DNA (基因序列)")
        @ColumnWidth(50)
        private String visualDna;

        @ExcelProperty("生成状态")
        @ColumnWidth(15)
        private String status;

        @ExcelProperty("UI 实景快照")
        @ColumnWidth(40)
        private File snapshot;

        @ExcelProperty("存储路径")
        @ColumnWidth(40)
        private String localPath;

        @ExcelProperty("Meta 数据")
        @ColumnWidth(40)
        private String metadataJson;
    }
}
