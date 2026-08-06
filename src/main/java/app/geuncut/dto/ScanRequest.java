package app.geuncut.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScanRequest {
	String scanType;

	String risk;

	Long capital;

	Long minProfit;

	Double minRoi;

	public static ScanRequest of(String scanType) {
		return ScanRequest.builder().scanType(scanType).build();
	}
}
