ALTER TABLE learning_materials
    DROP CHECK chk_learning_materials_content_edit_status;

ALTER TABLE learning_materials
    DROP COLUMN content_edit_status;
