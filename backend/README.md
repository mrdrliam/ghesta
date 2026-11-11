# سرور قسطا (Go)

سرور همگام‌سازی و حساب کاربری برای اپ اندروید قسطا. یک فایل اجرایی، دیتابیس SQLite (یک فایل)، بدون وابستگی به cgo.

## اجرا

```bash
cd backend
go test ./...              # تست‌ها
go run .                   # اجرا روی :8080 ، دیتابیس در ./data/ghesta.db
docker compose up --build  # یا با Docker
```

اپ در حالت debug به‌طور پیش‌فرض به `http://10.0.2.2:8080` وصل می‌شود (آدرس مک شما از داخل شبیه‌ساز اندروید).

## تنظیمات (متغیرهای محیطی)

| متغیر | پیش‌فرض | توضیح |
|---|---|---|
| `ADDR` | `:8080` | آدرس شنود |
| `DB_PATH` | `./data/ghesta.db` | فایل دیتابیس |
| `ALLOW_REGISTER` | `true` | `false` = فقط حساب‌های موجود وارد شوند |
| `TRUST_PROXY` | `false` | فقط پشت reverse proxy خودتان `true` کنید (برای IP واقعی) |
| `ACCESS_TTL` / `REFRESH_TTL` | `30m` / `2160h` | عمر توکن‌ها |
| `AUTH_RATE_PER_MIN` | `30` | درخواست احراز هویت در دقیقه برای هر IP |
| `MAX_LOGIN_FAILURES` / `LOCK_DURATION` | `5` / `15m` | قفل حساب بعد از رمز غلط |
| `MAX_RECORDS` | `5000` | سقف تعداد موارد هر کاربر |

## استقرار

- سرور خودش TLS ندارد؛ پشت Caddy یا nginx با HTTPS بگذارید (اپ در نسخه‌ی release فقط HTTPS را می‌پذیرد).
- از پوشه‌ی `/data` (یا `DB_PATH`) بکاپ بگیرید. فایل SQLite در حالت WAL است؛ با `sqlite3 ghesta.db ".backup out.db"` بکاپ امن بگیرید.

## API

همه‌ی پاسخ‌ها JSON هستند. خطاها: `{"error": "<code>", "message": "<متن فارسی>"}`.

| مسیر | توضیح |
|---|---|
| `POST /v1/auth/register` | `{username, password}` ← توکن‌ها + `recovery_code` (فقط یک بار) |
| `POST /v1/auth/login` | `{username, password}` ← توکن‌ها |
| `POST /v1/auth/refresh` | `{refresh_token}` ← توکن‌های جدید (قبلی‌ها باطل می‌شوند) |
| `POST /v1/auth/recover` | `{username, recovery_code, new_password}` ← توکن‌ها + کد بازیابی جدید |
| `POST /v1/auth/logout` | خروج از این دستگاه |
| `POST /v1/auth/change-password` | `{old_password, new_password}`؛ بقیه‌ی دستگاه‌ها خارج می‌شوند |
| `GET /v1/me` | نام کاربری |
| `POST /v1/account/delete` | `{password}` ← حذف حساب و همه‌ی داده‌ها |
| `POST /v1/sync` | `{cursor, changes:[{id, updated_at, deleted, data}]}` ← `{cursor, changes, accepted, rejected}` |

### همگام‌سازی

هر وام/بدهی یک «رکورد» است. هر نوشتن پذیرفته‌شده یک `seq` افزایشی برای همان کاربر می‌گیرد و کلاینت فقط تغییرات بعد از `cursor` خودش را می‌گیرد. در تعارض، رکوردی که `updated_at` جدیدتری دارد می‌ماند؛ اگر نوشتن رد شود، نسخه‌ی برنده در همان پاسخ به کلاینت برمی‌گردد. حذف‌ها به‌صورت `deleted: true` (tombstone) همگام می‌شوند.
