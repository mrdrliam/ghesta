# استقرار سرور قسطا روی کلاستر (k3s + Argo CD)

مسیر کار: **GitHub Actions** تست می‌کند و image را در **ghcr** می‌سازد، سپس digest آن را در
`k8s/overlays/kassa-prod/image-patch.yaml` ثبت می‌کند. **Argo CD** همین فایل را می‌خواند و روی کلاستر اجرا می‌کند
(فقط Argo روی کلاستر می‌نویسد).

```
push به main ─► تست ─► ساخت image ─► ghcr.io:443/mrdrliam/ghesta-server:<نسخه>-prod.<build>
                                         └► commit خودکار digest ─► Argo CD ─► namespace «ghesta»
```

## ساختار

| مسیر | محتوا |
|---|---|
| `k8s/base` | Deployment (یک replica، Recreate)، Service، PVC برای SQLite، ConfigMap، NetworkPolicy |
| `k8s/overlays/kassa-prod` | Ingress (فقط HTTPS)، Certificate (cert-manager)، پین کردن image |
| `k8s/argocd` | `project.yaml` و `prod.yaml` (AppProject و Application) |
| `scripts/record_release.py` | CI با آن digest را در overlay ثبت می‌کند (با تست) |
| `.github/workflows/backend.yml` | تست روی PR و push؛ ساخت و انتشار فقط روی `main` |

نسخه‌ی برنامه از `backend/VERSION` می‌آید؛ برای انتشار نسخه‌ی تازه آن را زیاد کنید.

## راه‌اندازی (یک‌بار)

۱. **DNS**: برای هاست `checkbox.api.kassadev.com` (در `certificate.yaml` و `ingress.yaml`؛ هر دو را با هم عوض کنید)
   رکورد A/AAAA به همان IP ورودی Traefik که بقیه‌ی `*.api.kassadev.com` استفاده می‌کنند بسازید. بدون آن، گواهی HTTPS صادر نمی‌شود.

۲. **push به GitHub**: بعد از اولین push روی `main`، workflow اجرا می‌شود و image و commit مربوط به digest ساخته می‌شود.

۳. **دسترسی Argo به مخزن**: اگر مخزن private است، مثل بقیه‌ی سرویس‌ها یک Secret در namespace `argocd` لازم است
   (توکن فقط‌خواندنی GitHub):

   ```bash
   kubectl -n argocd create secret generic ghesta-repository \
     --from-literal=type=git \
     --from-literal=url=https://github.com/mrdrliam/ghesta.git \
     --from-literal=username=mrdrliam \
     --from-literal=password=<GITHUB_READ_ONLY_TOKEN>
   kubectl -n argocd label secret ghesta-repository argocd.argoproj.io/secret-type=repository
   ```

۴. **دسترسی کلاستر به image**: بسته‌های ghcr به‌طور پیش‌فرض private هستند. یا بسته را در GitHub «Public» کنید،
   یا Secret دریافت image بسازید (نام آن باید `ghcr-pull` باشد؛ namespace باید قبلاً ساخته شده باشد):

   ```bash
   kubectl create namespace ghesta   # اگر Argo هنوز نساخته
   kubectl -n ghesta create secret docker-registry ghcr-pull \
     --docker-server=ghcr.io:443 --docker-username=mrdrliam --docker-password=<GITHUB_PACKAGES_READ_TOKEN>
   ```

۵. **ثبت در Argo** (فقط بعد از این‌که workflow اولین image را ساخت):

   ```bash
   kubectl apply -f k8s/argocd/project.yaml -f k8s/argocd/prod.yaml
   ```

۶. **بررسی**:

   ```bash
   kubectl -n ghesta get pods,pvc,ingress,certificate
   curl https://checkbox.api.kassadev.com/healthz     # {"status":"ok","version":"0.1.0"}
   ```

   سپس در اپ (نسخه‌ی release) همان آدرس به‌طور پیش‌فرض تنظیم است؛ ثبت‌نام کنید و کد بازیابی را نگه دارید.

## داده و پشتیبان

- پایگاه داده SQLite روی PVC با کلاس `local-fast` (سیاست `Retain`) است؛ حذف برنامه داده را پاک نمی‌کند.
- **یک replica و استراتژی Recreate عمداً انتخاب شده**؛ SQLite نباید هم‌زمان توسط دو pod باز شود. اگر زمانی نیاز به چند replica شد، باید به PostgreSQL مهاجرت کرد.
- هر ۲۴ ساعت یک snapshot سازگار در `/data/backups` ذخیره می‌شود (۷ تای آخر). این روی **همان دیسک** است؛
  برای محافظت در برابر خرابی نود، فایل‌ها را جایی بیرون از کلاستر (مثلاً MinIO) کپی کنید.
- بازگردانی: pod را متوقف کنید (`kubectl -n ghesta scale deploy/ghesta-server --replicas=0` — Argo با selfHeal برمی‌گرداند،
  پس ابتدا auto-sync را موقتاً خاموش کنید)، سپس فایل `ghesta-….db` را به‌جای `/data/ghesta.db` بگذارید و فایل‌های
  `ghesta.db-wal` و `ghesta.db-shm` را حذف کنید.

## بازگشت به نسخه‌ی قبل

در Git، commit مربوط به آخرین `chore(deploy)` را revert کنید؛ Argo به digest قبلی برمی‌گردد.

## تنظیمات و ملاحظات

- تنظیمات سرور در `k8s/base/configmap.yaml` است (ثبت‌نام باز/بسته با `ALLOW_REGISTER`، قفل حساب، …).
  اگر ثبت‌نام عمومی نمی‌خواهید، پس از ساخت حساب‌های لازم آن را `"false"` کنید.
- محدودیت درخواست برای هر IP عمداً بالا (۱۲۰ در دقیقه) است؛ چون بسیاری از گوشی‌ها پشت یک IP موبایل مشترک‌اند.
  حفاظت اصلی در برابر حدس رمز، قفل ۱۵ دقیقه‌ای حساب بعد از ۵ رمز غلط است.
- pod با سطح امنیتی `restricted` اجرا می‌شود (غیر root، فایل‌سیستم فقط‌خواندنی، بدون capability).
- فقط Traefik اجازه‌ی اتصال به pod را دارد (NetworkPolicy). HTTP ساده عمداً منتشر نمی‌شود.
