-- さんたく（taskboard） DB定義 / MySQL 8 以降
--
-- このファイルの内容は、JPA のエンティティ定義（domain パッケージ）と同じものです。
--
-- 本番プロファイル（prod）は spring.jpa.hibernate.ddl-auto=validate で動きます。
-- 起動時に、表の形がエンティティと合っているかを確かめるだけで、表は作りません。
-- そのため、空のデータベースに対しては、先にこのファイルを流す必要があります。
--
-- 開発プロファイル（dev）の H2 は ddl-auto=update なので、流さなくても動きます。
--
-- 外部キー制約を張っています（2026年10月）。
-- エンティティは @ManyToOne で結ばず、user_id などを数値の列として持つ作りのままです。
-- アプリ側（TaskService.findById / ProposalService.findSet）の持ち主の確認に加えて、
-- データベース側でも、行き先の無い参照が入らないようにしています。
--
-- 消したときの振る舞いは、参照の意味に合わせて3通りに分けています。
--   RESTRICT … 利用者を指す参照。タスクや提案が残っている利用者は消せない
--   CASCADE  … セットとカードの参照。セットを消せばカードも一緒に消える
--   SET NULL … カードからタスクを指す参照。タスクを消しても「出した記録」は残す

CREATE DATABASE IF NOT EXISTS taskboard
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE taskboard;

-- ---------------------------------------------------------------
-- users … 利用者。1人1行
-- 名前もメールアドレスも持たない。本人を見分けるのは google_sub だけ
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  google_sub        VARCHAR(64)  NOT NULL,
  created_at        DATETIME     NOT NULL,
  sound_enabled     BIT(1)       NULL DEFAULT b'1',
  confetti_enabled  BIT(1)       NULL DEFAULT b'1',
  main_use          VARCHAR(20)  NULL,
  order_pref        VARCHAR(20)  NULL,
  focus_level       VARCHAR(20)  NULL,
  profile_set_at    DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_google_sub (google_sub)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- tasks … タスク。1件1行
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasks (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  user_id           BIGINT       NOT NULL,
  title             VARCHAR(100) NOT NULL,
  detail            VARCHAR(300) NULL,
  difficulty        VARCHAR(10)  NULL,
  estimated_minutes INT          NULL,
  due_date          DATE         NULL,
  status            VARCHAR(10)  NOT NULL,
  sort_order        INT          NOT NULL,
  created_at        DATETIME     NOT NULL,
  completed_at      DATETIME     NULL,
  auto_complete_at  DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_tasks_user (user_id),
  CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- proposal_sets … 提案1回ぶん。スタート1回で1行
-- requested_at は「3案が出そろった時刻」。押した時刻ではない
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proposal_sets (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  user_id      BIGINT        NOT NULL,
  requested_at DATETIME      NOT NULL,
  decided_at   DATETIME      NULL,
  time_of_day  VARCHAR(10)   NULL,
  source       VARCHAR(20)   NULL,
  status       VARCHAR(10)   NOT NULL,
  prompt_text  VARCHAR(5000) NULL,
  PRIMARY KEY (id),
  KEY idx_proposal_sets_user (user_id),
  CONSTRAINT fk_proposal_sets_user FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- proposal_cards … 提案カード。1回につき3行
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proposal_cards (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  proposal_set_id   BIGINT       NOT NULL,
  position          INT          NOT NULL,
  title             VARCHAR(100) NOT NULL,
  category          VARCHAR(30)  NULL,
  detail            VARCHAR(300) NULL,
  granularity       VARCHAR(10)  NULL,
  difficulty        VARCHAR(10)  NULL,
  estimated_minutes INT          NULL,
  reason            VARCHAR(200) NULL,
  chosen            BIT(1)       NOT NULL,
  source_task_id    BIGINT       NULL,
  result_task_id    BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_proposal_cards_set (proposal_set_id),
  KEY idx_proposal_cards_source_task (source_task_id),
  KEY idx_proposal_cards_result_task (result_task_id),
  CONSTRAINT fk_proposal_cards_set FOREIGN KEY (proposal_set_id) REFERENCES proposal_sets (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_proposal_cards_source_task FOREIGN KEY (source_task_id) REFERENCES tasks (id)
    ON DELETE SET NULL,
  CONSTRAINT fk_proposal_cards_result_task FOREIGN KEY (result_task_id) REFERENCES tasks (id)
    ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- push_subscriptions … 通知の届け先。端末ごとに1行
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_subscriptions (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  endpoint   VARCHAR(500) NOT NULL,
  p256dh     VARCHAR(255) NOT NULL,
  auth       VARCHAR(255) NOT NULL,
  created_at DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_push_endpoint (endpoint),
  KEY idx_push_subscriptions_user (user_id),
  CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id)
    ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 接続用のユーザー
-- application-prod.yml が username: taskboard で接続します
-- ---------------------------------------------------------------
-- CREATE USER 'taskboard'@'localhost' IDENTIFIED BY '<パスワード>';
-- GRANT ALL PRIVILEGES ON taskboard.* TO 'taskboard'@'localhost';
-- FLUSH PRIVILEGES;

-- ---------------------------------------------------------------
-- 初期データは不要です。
-- 利用者の行は、はじめて Google でログインしたときに作られます。
-- ---------------------------------------------------------------
