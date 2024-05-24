package main

import (
	"encoding/base64"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
)

type User struct {
	Username     string `json:"username"`
	UID          int    `json:"uid"`
	PasswordHash string `json:"passwordHash"`
	Gecos        string `json:"gecos"`
	SSHKey       string `json:"sshPublicKey"`
	Enabled      *bool  `json:"enabled"`
}

var (
	users     []User
	usernames map[string]int
	uids      map[int]int
	instances map[string]string
)

const userData = `
[
  {
    "username": "dummy",
    "uid": 1,
    "passwordHash": "$2b$12$1gyKIjBc9/cT0MYkXX24xe1LjEUjNwgL4rEk8fDoO.vDQZzWkqrn.",
    "gecos": "dummy user",
    "sshPublicKey": "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDiEcu2czBfbQh6+A3DplO2DRHG4LmdUKLRPX1vFOpC30TZusio0cFcgi8TEQv9TlMwu2ujF/wn/0D2VwXiDGk/Rbeq9jgTLpVbDWg/3ZGxGSqjPV3fzl0NzmaSgF0+IZQaSr6OjGVpTmpk5G4d4qqFg4Shjjm+AwlgmruThHNS9KmdJ7Vru+rQ8LwcjuSWtBdf6JM3bjlw1swQt6776p+wTK51YSKdtFEE5yVZjVwxlcPre7sRiem0XwSCFsu9sAfUTbHNTfwQ8lVXbvgRGu9SoW0wwb0Qele1WXZ8YFF10KLxGKpb1u0NsXSIZrJZhk0nxKb5tGBSnKXquoAvLZfVEKc+AXw1sDSaKvZaDw0/GORoAVSt3LDKYydAlzpMw6am4fgcEzm0vwCieWvSxd9uLY9IxV4sx0n0ZcuG55Le2TaQMnSm5XQ8zHBFYOb9ux8h6TY6JO+HmSjoHXkGhILKg8Y7zpq0PWy7HUvWzQVVfShAMN/N2gZ3a2T7amg17/S0wtgvjxxpNtnLc0HnHjr/mwtBjrN8C4n+IYI13rqZzPPU1wZu5qiacmHmeR15XAktEFKrpuvViJcylksjwyl6aY9psm+dwocON/yA3pdJGA8mPvrnDpPkGpzqvTqqIMxQkgel46jF2B7+lLzq6wQOsb7Ct66CKKppM6kpVVHRWQ==",
    "enabled": null
  }
]
`

func loadUsers() {
	if err := json.Unmarshal([]byte(userData), &users); err != nil {
		log.Fatalf("Failed to decode users: %v", err)
	}

	usernames = make(map[string]int)
	uids = make(map[int]int)

	for i, user := range users {
		usernames[user.Username] = i
		uids[user.UID] = i
	}
}

func fetchUserInfo(identifier string, idType string) *User {
	switch idType {
	case "username":
		if pos, exists := usernames[identifier]; exists {
			return &users[pos]
		}
	case "uid":
		if uid, err := strconv.Atoi(identifier); err == nil {
			if pos, exists := uids[uid]; exists {
				return &users[pos]
			}
		}
	}
	return nil
}

func userHandler(w http.ResponseWriter, r *http.Request) {
	authHeader := r.Header.Get("Authorization")
	if authHeader == "" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	authValue, err := base64.StdEncoding.DecodeString(authHeader[len("Basic "):])
	if err != nil {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	parts := strings.SplitN(string(authValue), ":", 2)
	if len(parts) != 2 {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}
	instance, passwd := parts[0], parts[1]

	if pass, exists := instances[instance]; !exists || pass != passwd {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	loadUsers()

	pathParts := strings.Split(r.URL.Path, "/")
	if len(pathParts) != 6 {
		http.Error(w, "Invalid path", http.StatusBadRequest)
		return
	}

	identifier := pathParts[5]
	idType := r.URL.Query().Get("idType")
	userInfo := fetchUserInfo(identifier, idType)
	if userInfo == nil {
		http.Error(w, "No info for that user", http.StatusBadRequest)
		return
	}

	response := map[string]interface{}{
		"header": map[string]interface{}{
			"apiVersion":       "v1",
			"code":             "200",
			"service":          "users",
			"developerMessage": "",
			"userMessage":      "OK",
			"errorCode":        "1",
			"docLink":          "https://ega-archive.org",
			"errorStack":       "",
		},
		"response": map[string]interface{}{
			"numTotalResults": 1,
			"resultType":      "eu.crg.ega.microservice.dto.lega.v1.users.LocalEgaUser",
			"result":          []User{*userInfo},
		},
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func main() {
	if len(os.Args) < 3 {
		log.Fatalf("Usage: %s <host> <port>", os.Args[0])
	}

	host := os.Args[1]
	port := os.Args[2]

	instances = map[string]string{
		os.Getenv("CEGA_USERS_USER"): os.Getenv("CEGA_USERS_PASSWORD"),
	}

	http.HandleFunc("/username/", userHandler)

	addr := host + ":" + port
	log.Printf("Server running at %s", addr)
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
