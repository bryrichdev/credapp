package main

import (
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

// What the helper remembers: which CredCloud servers it's connected to, with each one's
// device token. It lives in the helper's folder, readable only by her account.

type serverConfig struct {
	Token string `json:"token"`
	Email string `json:"email,omitempty"`
}

type config struct {
	Servers map[string]*serverConfig `json:"servers"`
	// Home is the CredCloud it was installed from: where to send her to connect it again.
	Home string `json:"home,omitempty"`
}

// helperDir is the helper's folder: its settings and its Chrome profile.
// On a Mac, ~/Library/Application Support/CredCloud Helper.
func helperDir() string {
	if dir := os.Getenv("CREDCLOUD_HELPER_HOME"); dir != "" {
		return dir
	}
	base, err := os.UserConfigDir()
	if err != nil {
		base = os.TempDir()
	}
	return filepath.Join(base, "CredCloud Helper")
}

func configPath() string { return filepath.Join(helperDir(), "config.json") }

func loadConfig() config {
	cfg := config{Servers: map[string]*serverConfig{}}
	if data, err := os.ReadFile(configPath()); err == nil {
		json.Unmarshal(data, &cfg)
	}
	if cfg.Servers == nil {
		cfg.Servers = map[string]*serverConfig{}
	}
	return cfg
}

func saveConfig(cfg config) error {
	if err := os.MkdirAll(helperDir(), 0o700); err != nil {
		return err
	}
	data, _ := json.MarshalIndent(cfg, "", "  ")
	tmp := configPath() + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, configPath())
}

// connectAtInstall remembers where the helper was installed from and, with a code, connects
// to it there and then. Without one (a plain download), the helper asks when it first runs.
func connectAtInstall(server, code string) (string, error) {
	cfg := loadConfig()
	cfg.Home = server
	if err := saveConfig(cfg); err != nil {
		return "", err
	}
	if code == "" {
		return "", nil
	}
	client, email, err := pair(server, code)
	if err != nil {
		return "", err
	}
	cfg.Servers[server] = &serverConfig{Token: client.Token, Email: email}
	return email, saveConfig(cfg)
}

// computerName is what CredCloud lists this computer as: "Jane's MacBook Air (Mac)".
func computerName() string {
	name := ""
	if runtime.GOOS == "darwin" {
		if out, err := exec.Command("/usr/sbin/scutil", "--get", "ComputerName").Output(); err == nil {
			name = strings.TrimSpace(string(out))
		}
	}
	if name == "" {
		name, _ = os.Hostname()
	}
	system := map[string]string{"darwin": "Mac", "windows": "Windows"}[runtime.GOOS]
	if system == "" {
		system = runtime.GOOS
	}
	return name + " (" + system + ")"
}
