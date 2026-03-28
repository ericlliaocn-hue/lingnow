package cc.lingnow.dto;

import cc.lingnow.model.ProjectManifest;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ProjectHistoryDto {
    private String id;
    private String userIntent;
    private ProjectManifest.ProjectStatus status;
    private Date createdAt;
    private long lastModified;
    private String version;
}
