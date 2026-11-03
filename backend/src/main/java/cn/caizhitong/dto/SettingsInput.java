package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public record SettingsInput(
 @NotNull @Min(0) Long revision,
 @NotBlank @Size(max=40) String systemName,@NotBlank @Size(max=200) String systemDescription,
 @NotBlank @Size(max=64) String businessZone,
 @NotNull @Min(1990) @Max(2200) Integer academicYear,
 @NotNull LocalDate autumnStart,@NotNull LocalDate autumnEnd,@NotNull LocalDate springStart,@NotNull LocalDate springEnd,
 @NotNull @Size(max=40) List<@NotBlank @Email(message="通知收件人邮箱格式不正确") @Size(max=254) String> notificationRecipients){
 @AssertTrue(message="学期日期须位于该学年 9 月至次年 8 月，秋季先于春季且范围不能重叠")
 public boolean isCalendarValid(){if(academicYear==null||autumnStart==null||autumnEnd==null||springStart==null||springEnd==null)return true;return !autumnStart.isBefore(LocalDate.of(academicYear,9,1))&&!autumnStart.isAfter(autumnEnd)&&autumnEnd.isBefore(springStart)&&!springStart.isAfter(springEnd)&&!springEnd.isAfter(LocalDate.of(academicYear+1,8,31));}
}
