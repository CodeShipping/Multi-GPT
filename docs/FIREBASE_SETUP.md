# Firebase Realtime Database Setup for Local AI Models

This guide explains how to upload the model catalog to Firebase Realtime Database.

## Method 1: Firebase Console (Easiest)

### Step 1: Open Firebase Console
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project (or create one)
3. Navigate to **Build** → **Realtime Database**

### Step 2: Create Database (if not exists)
1. Click **Create Database**
2. Choose a location (closest to your users)
3. Start in **locked mode** (we'll set rules later)

### Step 3: Import JSON Data
1. Click the **⋮** (three dots) menu at the top
2. Select **Import JSON**
3. Upload the `firebase_local_models_schema.json` file
4. Click **Import**

### Step 4: Verify Structure
Your database should now look like:
```
├── localModels
│   ├── version: 1
│   ├── lastUpdated: 1706745600000
│   ├── families
│   │   ├── 0
│   │   │   ├── id: "llama3"
│   │   │   ├── name: "Llama 3.2"
│   │   │   └── ...
│   │   └── ...
│   └── models
│       ├── 0
│       │   ├── id: "llama-3.2-1b-q4"
│       │   ├── name: "Llama 3.2 1B"
│       │   └── ...
│       └── ...
```

## Method 2: Firebase CLI

### Step 1: Install Firebase CLI
```bash
npm install -g firebase-tools
```

### Step 2: Login and Initialize
```bash
firebase login
firebase init database
```

### Step 3: Deploy Data
Create a script to upload:

```bash
# Upload the JSON data
firebase database:set /localModels docs/firebase_local_models_schema.json --project YOUR_PROJECT_ID
```

Or use the REST API:
```bash
curl -X PUT \
  'https://YOUR_PROJECT_ID.firebaseio.com/localModels.json' \
  -d @docs/firebase_local_models_schema.json
```

## Method 3: Admin SDK (Programmatic)

```javascript
const admin = require('firebase-admin');

// Initialize with your service account
admin.initializeApp({
  credential: admin.credential.cert('./serviceAccountKey.json'),
  databaseURL: 'https://YOUR_PROJECT_ID.firebaseio.com'
});

const db = admin.database();
const data = require('./firebase_local_models_schema.json');

db.ref('/').set(data)
  .then(() => {
    console.log('Data uploaded successfully!');
    process.exit(0);
  })
  .catch((error) => {
    console.error('Error uploading data:', error);
    process.exit(1);
  });
```

## Database Rules

Set these rules to allow read access:

```json
{
  "rules": {
    "localModels": {
      ".read": true,
      ".write": false
    }
  }
}
```

Or for authenticated users only:
```json
{
  "rules": {
    "localModels": {
      ".read": "auth != null",
      ".write": false
    }
  }
}
```

## Updating the Catalog

When you need to update models:

1. **Update the version number** - Increment `version` to trigger client refresh
2. **Update lastUpdated** - Set to current timestamp in milliseconds
3. **Re-import** - Use any method above to update

### Example: Adding a New Model

```json
{
  "id": "new-model-id",
  "name": "New Model Name",
  "description": "Description here",
  "size": 1000000000,
  "downloadUrl": "https://huggingface.co/...",
  "fileName": "model.gguf",
  "performance": {
    "tokensPerSecond": 20,
    "memoryRequired": 2000000000,
    "cpuIntensive": false,
    "gpuAccelerated": true,
    "rating": "BALANCED"
  },
  "useCases": ["CHAT", "GENERAL"],
  "quantization": "Q4_K_M",
  "parameters": "1B",
  "contextLength": 8192,
  "familyId": "family-id",
  "isRecommended": false,
  "isEnabled": true
}
```

## Disabling a Model

To disable a model without removing it, set `isEnabled: false`:
```json
{
  "id": "model-to-disable",
  "isEnabled": false,
  ...
}
```

The app will filter out disabled models automatically.

## Cost Optimization Tips

1. **Version Check First** - The app only fetches the `version` field on startup (lightweight)
2. **Local Caching** - Full catalog is cached locally for 24 hours
3. **Bandwidth Rules** - Consider Firebase's bandwidth quotas on free tier
4. **CDN for Model Files** - Model files are downloaded from Hugging Face, not Firebase

## Troubleshooting

### "Permission denied" error
- Check your database rules allow read access
- Verify the database URL in your app matches

### Data not showing in app
- Check Firebase console shows correct structure
- Verify `isEnabled: true` for models
- Check network connectivity

### Version not updating
- Increment the `version` number
- Update `lastUpdated` timestamp
- Wait for the app's version check interval (1 hour)
