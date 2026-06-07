/*
 Navicat Premium Dump SQL

 Source Server         : hjan.xin
 Source Server Type    : PostgreSQL
 Source Server Version : 160014 (160014)
 Source Host           : hjan.xin:5432
 Source Catalog        : act-one
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 160014 (160014)
 File Encoding         : 65001

 Date: 07/06/2026 21:53:00
*/


-- ----------------------------
-- Table structure for t_script_chapter
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_script_chapter";
CREATE TABLE "public"."t_script_chapter"
(
    "id"            varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "project_id"    varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "chapter_index" int4                                        NOT NULL,
    "title"         varchar(256) COLLATE "pg_catalog"."default" NOT NULL,
    "content"       text COLLATE "pg_catalog"."default"         NOT NULL,
    "char_count"    int4                                        NOT NULL DEFAULT 0,
    "content_hash"  varchar(64) COLLATE "pg_catalog"."default",
    "create_time"   timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time"   timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"       int2                                        NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_script_chapter" OWNER TO "postgres";
COMMENT ON TABLE "public"."t_script_chapter" IS '小说章节表';

-- ----------------------------
-- Table structure for t_script_conversion_task
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_script_conversion_task";
CREATE TABLE "public"."t_script_conversion_task"
(
    "id"             varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
    "project_id"     varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
    "draft_id"       varchar(32) COLLATE "pg_catalog"."default",
    "status"         varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'pending'::character varying,
    "progress"       int4                                       NOT NULL DEFAULT 0,
    "message"        varchar(512) COLLATE "pg_catalog"."default",
    "error_message"  text COLLATE "pg_catalog"."default",
    "schema_version" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '1.0'::character varying,
    "model_id"       varchar(128) COLLATE "pg_catalog"."default",
    "start_time"     timestamp(6),
    "end_time"       timestamp(6),
    "create_time"    timestamp(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time"    timestamp(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        int2                                       NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_script_conversion_task" OWNER TO "postgres";
COMMENT ON COLUMN "public"."t_script_conversion_task"."status" IS '任务状态：pending/running/success/failed/canceled';
COMMENT ON TABLE "public"."t_script_conversion_task" IS '剧本转换任务表';

-- ----------------------------
-- Table structure for t_script_draft
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_script_draft";
CREATE TABLE "public"."t_script_draft"
(
    "id"                varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "project_id"        varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "task_id"           varchar(32) COLLATE "pg_catalog"."default",
    "title"             varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "schema_version"    varchar(32) COLLATE "pg_catalog"."default"  NOT NULL DEFAULT '1.0'::character varying,
    "yaml_content"      text COLLATE "pg_catalog"."default"         NOT NULL,
    "validation_status" varchar(32) COLLATE "pg_catalog"."default"  NOT NULL DEFAULT 'unchecked'::character varying,
    "validation_error"  text COLLATE "pg_catalog"."default",
    "create_time"       timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time"       timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"           int2                                        NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_script_draft" OWNER TO "postgres";
COMMENT ON COLUMN "public"."t_script_draft"."validation_status" IS '校验状态：unchecked/valid/invalid';
COMMENT ON TABLE "public"."t_script_draft" IS '剧本 YAML 草稿表';

-- ----------------------------
-- Table structure for t_script_draft_revision
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_script_draft_revision";
CREATE TABLE "public"."t_script_draft_revision"
(
    "id"             varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
    "draft_id"       varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
    "revision_no"    int4                                       NOT NULL,
    "yaml_content"   text COLLATE "pg_catalog"."default"        NOT NULL,
    "change_summary" varchar(512) COLLATE "pg_catalog"."default",
    "created_by"     varchar(64) COLLATE "pg_catalog"."default",
    "create_time"    timestamp(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"        int2                                       NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_script_draft_revision" OWNER TO "postgres";
COMMENT ON TABLE "public"."t_script_draft_revision" IS '剧本草稿版本表';

-- ----------------------------
-- Table structure for t_script_project
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_script_project";
CREATE TABLE "public"."t_script_project"
(
    "id"          varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "title"       varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "description" varchar(512) COLLATE "pg_catalog"."default",
    "status"      varchar(32) COLLATE "pg_catalog"."default"  NOT NULL DEFAULT 'draft'::character varying,
    "created_by"  varchar(64) COLLATE "pg_catalog"."default",
    "updated_by"  varchar(64) COLLATE "pg_catalog"."default",
    "create_time" timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"     int2                                        NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_script_project" OWNER TO "postgres";
COMMENT ON COLUMN "public"."t_script_project"."status" IS '项目状态：draft/converting/completed/archived';
COMMENT ON TABLE "public"."t_script_project" IS '小说转剧本项目表';

-- ----------------------------
-- Table structure for t_script_stage_result
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_script_stage_result";
CREATE TABLE "public"."t_script_stage_result"
(
    "id"          varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
    "task_id"     varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
    "stage"       varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
    "result_json" text COLLATE "pg_catalog"."default",
    "result_hash" varchar(64) COLLATE "pg_catalog"."default",
    "attempt"     int4                                       NOT NULL DEFAULT 0,
    "create_time" timestamp(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"     int4                                       NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_script_stage_result" OWNER TO "postgres";
COMMENT ON COLUMN "public"."t_script_stage_result"."id" IS '主键ID（雪花算法）';
COMMENT ON COLUMN "public"."t_script_stage_result"."task_id" IS '任务ID';
COMMENT ON COLUMN "public"."t_script_stage_result"."stage" IS '阶段标识';
COMMENT ON COLUMN "public"."t_script_stage_result"."result_json" IS '结果JSON';
COMMENT ON COLUMN "public"."t_script_stage_result"."result_hash" IS '结果哈希';
COMMENT ON COLUMN "public"."t_script_stage_result"."attempt" IS '重试次数';
COMMENT ON COLUMN "public"."t_script_stage_result"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."t_script_stage_result"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."t_script_stage_result"."deleted" IS '逻辑删除标识（0-未删除，1-已删除）';

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS "public"."t_user";
CREATE TABLE "public"."t_user"
(
    "id"          varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "username"    varchar(64) COLLATE "pg_catalog"."default"  NOT NULL,
    "password"    varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
    "role"        varchar(32) COLLATE "pg_catalog"."default"  NOT NULL,
    "avatar"      varchar(512) COLLATE "pg_catalog"."default",
    "create_time" timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "update_time" timestamp(6)                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted"     int2                                        NOT NULL DEFAULT 0
)
;
ALTER TABLE "public"."t_user" OWNER TO "postgres";
COMMENT ON COLUMN "public"."t_user"."username" IS '用户名';
COMMENT ON COLUMN "public"."t_user"."password" IS '密码';
COMMENT ON COLUMN "public"."t_user"."role" IS '角色：admin/user';
COMMENT ON TABLE "public"."t_user" IS '系统用户表';

-- ----------------------------
-- Indexes structure for table t_script_chapter
-- ----------------------------
CREATE INDEX "idx_script_chapter_project" ON "public"."t_script_chapter" USING btree (
    "project_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "chapter_index" "pg_catalog"."int4_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Uniques structure for table t_script_chapter
-- ----------------------------
ALTER TABLE "public"."t_script_chapter"
    ADD CONSTRAINT "uk_script_chapter_project_index" UNIQUE ("project_id", "chapter_index");

-- ----------------------------
-- Primary Key structure for table t_script_chapter
-- ----------------------------
ALTER TABLE "public"."t_script_chapter"
    ADD CONSTRAINT "t_script_chapter_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_script_conversion_task
-- ----------------------------
CREATE INDEX "idx_script_task_project" ON "public"."t_script_conversion_task" USING btree (
    "project_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "create_time" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
    );
CREATE INDEX "idx_script_task_status" ON "public"."t_script_conversion_task" USING btree (
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "deleted" "pg_catalog"."int2_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Primary Key structure for table t_script_conversion_task
-- ----------------------------
ALTER TABLE "public"."t_script_conversion_task"
    ADD CONSTRAINT "t_script_conversion_task_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_script_draft
-- ----------------------------
CREATE INDEX "idx_script_draft_project" ON "public"."t_script_draft" USING btree (
    "project_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "update_time" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
    );

-- ----------------------------
-- Primary Key structure for table t_script_draft
-- ----------------------------
ALTER TABLE "public"."t_script_draft"
    ADD CONSTRAINT "t_script_draft_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_script_draft_revision
-- ----------------------------
CREATE INDEX "idx_script_revision_draft" ON "public"."t_script_draft_revision" USING btree (
    "draft_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "revision_no" "pg_catalog"."int4_ops" DESC NULLS FIRST
    );

-- ----------------------------
-- Uniques structure for table t_script_draft_revision
-- ----------------------------
ALTER TABLE "public"."t_script_draft_revision"
    ADD CONSTRAINT "uk_script_draft_revision" UNIQUE ("draft_id", "revision_no");

-- ----------------------------
-- Primary Key structure for table t_script_draft_revision
-- ----------------------------
ALTER TABLE "public"."t_script_draft_revision"
    ADD CONSTRAINT "t_script_draft_revision_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_script_project
-- ----------------------------
CREATE INDEX "idx_script_project_status" ON "public"."t_script_project" USING btree (
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "deleted" "pg_catalog"."int2_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Primary Key structure for table t_script_project
-- ----------------------------
ALTER TABLE "public"."t_script_project"
    ADD CONSTRAINT "t_script_project_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table t_script_stage_result
-- ----------------------------
CREATE INDEX "idx_t_script_stage_result_stage" ON "public"."t_script_stage_result" USING btree (
    "stage" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_t_script_stage_result_task_id" ON "public"."t_script_stage_result" USING btree (
    "task_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Primary Key structure for table t_script_stage_result
-- ----------------------------
ALTER TABLE "public"."t_script_stage_result"
    ADD CONSTRAINT "t_script_stage_result_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table t_user
-- ----------------------------
ALTER TABLE "public"."t_user"
    ADD CONSTRAINT "uk_user_username" UNIQUE ("username");

-- ----------------------------
-- Primary Key structure for table t_user
-- ----------------------------
ALTER TABLE "public"."t_user"
    ADD CONSTRAINT "t_user_pkey" PRIMARY KEY ("id");
