"""document and ingest_job

Revision ID: 0001
"""

import sqlalchemy as sa

from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "document",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("collection", sa.String(40), nullable=False),
        sa.Column("source_type", sa.String(10), nullable=False),
        sa.Column("source_ref", sa.String(1000), nullable=False),
        sa.Column("title", sa.String(500), nullable=False),
        sa.Column("content_hash", sa.String(64), nullable=False, server_default=""),
        sa.Column("status", sa.String(10), nullable=False, server_default="PENDING"),
        sa.Column("error", sa.Text),
        sa.Column("chunk_count", sa.Integer, nullable=False, server_default="0"),
        sa.Column("remote_modified", sa.String(40)),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("collection", "source_ref", name="uq_document_source"),
    )
    op.create_index("ix_document_collection", "document", ["collection"])
    op.create_table(
        "ingest_job",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("collection", sa.String(40), nullable=False),
        sa.Column("kind", sa.String(20), nullable=False),
        sa.Column("status", sa.String(10), nullable=False, server_default="QUEUED"),
        sa.Column("progress", sa.Integer, nullable=False, server_default="0"),
        sa.Column("error", sa.Text),
        sa.Column("payload", sa.JSON, nullable=False),
        sa.Column("failures", sa.JSON, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True)),
    )


def downgrade() -> None:
    op.drop_table("ingest_job")
    op.drop_index("ix_document_collection", "document")
    op.drop_table("document")
