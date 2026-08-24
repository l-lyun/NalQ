package com.openmd.server.auth.dto.command;

import com.openmd.server.auth.dto.model.TermsAgreement;
import java.util.List;

public record SignUpCommand(
	String signUpToken,
	String password,
	String nickname,
	List<TermsAgreement> agreements
) {
}
