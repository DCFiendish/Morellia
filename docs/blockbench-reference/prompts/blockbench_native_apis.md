# Blockbench v5.0 Native API Security Model

**CRITICAL: Read this carefully before using any Node.js APIs or native modules in Blockbench plugins.**

## Key Changes from Previous Versions

### 1. Global Module Variables Removed
- **OLD (pre-v5.0)**: All Node.js APIs were globally available (e.g., `fs`, `os`, `child_process`)
- **NEW (v5.0+)**: Most global variables have been removed
- **EXCEPTION**: `PathModule` is still globally available for Node's `path` module

### 2. Module Access via `requireNativeModule()`
```javascript
const os = requireNativeModule('os');
const child_process = requireNativeModule('child_process', {
    message: 'This permission is required to open ffmpeg and encode the video.'
});
```
`requireNativeModule()` opens a user permission prompt for restricted modules, returns the module synchronously if accepted, or `undefined` if denied. Always check before use.

### 3. Module Categories

**Safe (no permission required)**: `path`, `crypto`, `events`, `zlib`, `timers`, `url`, `string_decoder`, `querystring`

**Restricted (require user permission)**: `fs`, `child_process`, `electron`, `https`, `net`, `tls`, `util`, `os`, `v8`

### 4. Scoped File System Access
```javascript
const scoped_fs = requireNativeModule('fs', {
    scope: 'C:/path/to/directory'
});
```
Writes outside the scope throw an error.

### 5. SystemInfo Interface
```javascript
SystemInfo.platform          // 'win32' | 'darwin' | 'linux'
SystemInfo.home_directory
SystemInfo.arch              // 'x64' | 'arm64'
SystemInfo.appdata_directory
SystemInfo.os_version
```

## Summary Checklist

- [ ] Use `requireNativeModule()` instead of `require()`
- [ ] Always check if module is defined before use
- [ ] Request permissions on-demand, not during plugin load
- [ ] Provide clear permission messages
- [ ] Use `SystemInfo` for basic system info instead of `os`
- [ ] Consider scoped file system access for better security
- [ ] Use `PathModule` for path operations (no permission needed)
- [ ] Test with user denying permissions to ensure graceful failures
