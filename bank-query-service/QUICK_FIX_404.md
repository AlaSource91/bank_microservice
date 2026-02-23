# 🆘 QUICK FIX: Account Not Found (404)

## Account Number: `AE202624542112`

---

## ⚡ Immediate Actions (Try in Order)

### 1️⃣ Run Diagnostic Tool
```powershell
.\diagnose-account.ps1 -AccountNumber "AE202624542112"
```

This will automatically check:
- ✓ Account in Bank Simulator Service
- ✓ Account in Bank Query Service
- ✓ Service health status
- ✓ MongoDB database
- ✓ Available accounts

---

### 2️⃣ Check if Account Exists in Source (Bank Simulator)
```bash
curl http://localhost:8080/api/v1/accounts/AE202624542112
```

**If 404:** Account doesn't exist → Create it first (see #3)  
**If 200:** Account exists → Event processing issue (see #4)

---

### 3️⃣ Create Account in Bank Simulator Service
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "accountHolderName": "Test User",
    "accountType": "PERSONAL",
    "initialBalance": 1000.00
  }'
```

**Then wait 10 seconds** and try querying again.

---

### 4️⃣ Check Event Processing Status

**View Bank Query Service Logs:**
```bash
# Look for these patterns:
"AccountReadModel created successfully for account number: AE202624542112"
# OR
"Error handling AccountCreated event"
```

**Check MongoDB:**
```javascript
use bank_query_db
db.accounts.find({ accountNumber: "AE202624542112" })
```

---

### 5️⃣ Verify Kafka Consumer is Working
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group bank-query-service-group \
  --describe
```

**Look for:**
- STATE = Stable ✓
- LAG = 0 ✓ (no pending messages)

**If LAG > 0:** Messages are pending, wait for processing

---

### 6️⃣ Clear Redis Cache (If Needed)
```bash
redis-cli
FLUSHDB
# Or specific key:
DEL accountDetails::AE202624542112
```

---

## 🔍 Quick Checks

### Service Status
```bash
# Bank Simulator Service
curl http://localhost:8080/actuator/health

# Bank Query Service
curl http://localhost:5052/actuator/health
```

### List All Accounts in Read Model
```bash
curl "http://localhost:5052/api/v1/query/accounts?page=0&size=10"
```

### Check Kafka Topic
```bash
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic bank-events \
  --from-beginning \
  | grep "AE202624542112"
```

---

## 🎯 Most Common Solutions

### ✅ Solution 1: Account Doesn't Exist
```bash
# Create account first
POST http://localhost:8080/api/v1/accounts
# Wait 10 seconds
# Query again
GET http://localhost:5052/api/v1/query/accounts/AE202624542112
```

### ✅ Solution 2: Event Processing Delay
```bash
# Just wait 10-15 seconds
# Events are processed asynchronously
# Then try again
```

### ✅ Solution 3: Consumer Not Running
```bash
# Restart Bank Query Service
# Check logs for Kafka connection
# Verify consumer group is active
```

### ✅ Solution 4: Kafka Not Running
```bash
# Start Kafka and Zookeeper
# Restart Bank Query Service
# Events will be reprocessed
```

---

## 📊 Expected Timeline

```
T+0s    → Account created in Bank Simulator
T+1s    → Event published to Kafka
T+2s    → Event consumed by Bank Query Service
T+3s    → Account available in read model
T+4s    → Query returns 200 OK
```

**If it takes longer:** Check logs for errors

---

## 🚨 Error Scenarios

### Scenario A: Account in Simulator, Not in Query Service
**Cause:** Event not processed yet or failed  
**Fix:** Check logs, wait, or restart consumer

### Scenario B: Account Not in Either Service
**Cause:** Never created  
**Fix:** Create account in Bank Simulator first

### Scenario C: Kafka Down
**Cause:** Kafka not running  
**Fix:** Start Kafka, restart services

### Scenario D: MongoDB Connection Failed
**Cause:** MongoDB not running  
**Fix:** Start MongoDB, check connection string

---

## 🎓 Understanding the Flow

```
1. Create Account in Bank Simulator
   POST /api/v1/accounts
   ↓
2. Account saved to PostgreSQL
   ↓
3. Event published to Kafka topic "bank-events"
   ↓
4. Bank Query Service consumes event
   ↓
5. AccountEventHandler processes event
   ↓
6. Account saved to MongoDB read model
   ↓
7. Query returns account
   GET /api/v1/query/accounts/{accountNumber}
```

---

## 📞 Need More Help?

1. **Read Full Guide:** `TROUBLESHOOTING_404_ERRORS.md`
2. **Run Diagnostic:** `.\diagnose-account.ps1 -AccountNumber "AE202624542112"`
3. **Check Logs:** Look for ERROR or WARN messages
4. **Enable DEBUG:** Set logging level to DEBUG in `application.yaml`

---

## ✅ Success Checklist

- [ ] Bank Simulator Service is running (port 8080)
- [ ] Bank Query Service is running (port 5052)
- [ ] PostgreSQL is running (Simulator DB)
- [ ] MongoDB is running (Query DB)
- [ ] Redis is running (Cache)
- [ ] Kafka + Zookeeper are running
- [ ] Health checks return "UP"
- [ ] Consumer group is "Stable"
- [ ] Consumer lag is 0

---

**Quick Test After Fix:**
```bash
# Should return 200 OK with account details
curl http://localhost:5052/api/v1/query/accounts/AE202624542112
```

