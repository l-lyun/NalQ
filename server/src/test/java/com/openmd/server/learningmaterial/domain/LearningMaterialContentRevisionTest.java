package com.openmd.server.learningmaterial.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LearningMaterialContentRevisionTest {

  @Test
  void usesUtf8Sha256AsLowercaseHex() {
    assertEquals(
        "cb0f24046b508710d6315e71bd9b21b920cf15301b0cf055dc9569c507576ea3",
        LearningMaterialContentRevision.from("내용"));
  }

  @Test
  void acceptsOnlyCanonicalLowercaseSha256() {
    assertTrue(
        LearningMaterialContentRevision.isValid(
            "cb0f24046b508710d6315e71bd9b21b920cf15301b0cf055dc9569c507576ea3"));
    assertFalse(
        LearningMaterialContentRevision.isValid(
            "CB0F24046B508710D6315E71BD9B21B920CF15301B0CF055DC9569C507576EA3"));
    assertFalse(LearningMaterialContentRevision.isValid("cb0f2404"));
    assertFalse(LearningMaterialContentRevision.isValid(null));
  }
}
