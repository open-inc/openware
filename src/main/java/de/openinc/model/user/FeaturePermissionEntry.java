package de.openinc.model.user;

public class FeaturePermissionEntry {
	public static final String FEATURE_MODE_BLOCK = "block";
	public static final String FEATURE_MODE_ALLOW = "allow";
	private String mode;
	private String subject;
	private String method;
	private boolean group;

	public FeaturePermissionEntry(String mode, String subject, String method, boolean group) {
		super();
		this.mode = mode.toLowerCase();
		this.subject = subject;
		this.method = group ? method : method.toLowerCase();
		this.group = group;
	}

	public String getMode() {
		return mode;
	}

	public String getSubject() {
		return subject;
	}

	public String getMethod() {
		return method;
	}

	public boolean isGroup() {
		return group;
	}

	@Override
	public String toString() {
		return String.format("OW:%s:%s:%s:%s", this.group ? "GROUP" : "SINGLE", this.mode, this.method, this.subject);
	}

	public static FeaturePermissionEntry parse(String permissionString) {
		// FORMAT
		// 0_OW:1_GROUP[GROUP/SINGLE]:2_MODE[BLOCK/ALLOW]:3_METHOD[STRING]:4_SUBJECT[STRING]
		if (permissionString == null) {
			throw new IllegalArgumentException("Permission string to be parsed must not be null");
		}
		String[] pParts = permissionString.split(":");
		if (pParts.length != 5) {
			throw new IllegalArgumentException(
					"Permission string must be of form OW:[GROUP/SINGLE]:[BLOCK/ALLOW]:METHOD:SUBJECT");
		}
		if (!(pParts[1]	.toLowerCase()
						.equals("group")
				|| pParts[1].toLowerCase()
							.equals("single"))) {
			throw new IllegalArgumentException("Permission type must be of GROUP or SINGLE");
		}
		if (!(pParts[2]	.toLowerCase()
						.equals("allow")
				|| pParts[2].toLowerCase()
							.equals("block"))) {
			throw new IllegalArgumentException("Permission Mode must be of ALlOW or BLOCK");
		}
		return new FeaturePermissionEntry(pParts[2], pParts[4], pParts[3], pParts[1].toLowerCase()
																					.equals("group"));
	}
}