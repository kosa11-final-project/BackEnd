# Production deployment package

This directory contains the version-controlled files that Jenkins runs on the
Application EC2 instance. Oracle Database Free and Nginx run directly on the
host. Spring Boot, Redis, and RabbitMQ run as Docker containers.

## Server layout

```text
/opt/stockit/
├── config/app.env                 # rendered from Parameter Store, mode 0600
├── runtime/deployment.env         # non-secret image tags and paths
├── runtime/active-slot            # blue or green
└── infra/                         # this version-controlled directory
```

Copy the Nginx upstream files to `/etc/nginx/stockit/` and initially point
`upstream.conf` to `upstream-blue.conf`. Use
`stockfit-api.bootstrap.conf` while issuing the first Let's Encrypt certificate,
then replace it with `stockfit-api.https.conf`.

## Environment files

- Copy `env/deployment.env.example` to `/opt/stockit/runtime/deployment.env`.
- Render `/opt/stockit/config/app.env` from Parameter Store and set mode `0600`.
- AI and ML settings intentionally remain disabled until their URLs and keys
  are available.
- Never commit the rendered files or any credentials.

## First deployment

Authenticate Docker to ECR, then run:

```bash
sudo bash /opt/stockit/infra/scripts/deploy-blue-green.sh <git-commit-sha>
```

The script pulls the inactive slot, waits for `/actuator/health`, validates and
reloads Nginx, records the active slot, and stops the previous slot after a
drain period. Run deployments outside the 00:20-04:10 KST batch window.

Rollback uses the image tag retained in the inactive slot:

```bash
sudo bash /opt/stockit/infra/scripts/rollback.sh
```

## Oracle backup prerequisites

Create the directory on the database host and grant it to Oracle:

```sql
CREATE OR REPLACE DIRECTORY STOCKIT_BACKUP_DIR AS '/opt/oracle/backup/stockit';
GRANT READ, WRITE ON DIRECTORY STOCKIT_BACKUP_DIR TO STOCKIT;
```

Install the AWS CLI, attach an EC2 role that can upload only to the backup
bucket, copy `env/oracle-backup.env.example` to
`/etc/stockit/oracle-backup.env` with owner `root:oinstall` and mode `0640`,
create `/opt/oracle/backup/stockit` with owner `oracle:oinstall` and mode
`0750`, and enable the timer. If Oracle is installed at another path, change
`ORACLE_HOME` and `PATH` in the environment file first.

The `10.0.0.0/16` Actuator allow-list in both Nginx templates is a placeholder.
Replace it with the actual private subnet CIDR of the monitoring EC2 before
enabling the site.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now stockfit-oracle-backup.timer
```

The S3 bucket lifecycle policy, rather than this script, is responsible for
deleting backups after seven days.
