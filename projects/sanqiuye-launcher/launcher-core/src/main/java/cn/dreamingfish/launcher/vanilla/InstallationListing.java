package cn.dreamingfish.launcher.vanilla;
import java.util.List;
public record InstallationListing(List<InstallationReceipt> installations,List<Problem> problems){public InstallationListing{installations=List.copyOf(installations);problems=List.copyOf(problems);}public record Problem(String installationId,String reason){}}
