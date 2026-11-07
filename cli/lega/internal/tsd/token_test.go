package tsd

import (
	"testing"

	"github.com/golang-jwt/jwt/v5"
)

func signed(t *testing.T, claims jwt.MapClaims) string {
	t.Helper()
	token, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte("test-only-key"))
	if err != nil {
		t.Fatal(err)
	}
	return token
}

func TestUserClaim(t *testing.T) {
	tests := []struct {
		name    string
		token   string
		want    string
		wantErr bool
	}{
		{name: "user claim", token: signed(t, jwt.MapClaims{"user": "p969-ega-user"}), want: "p969-ega-user"},
		{name: "no user claim", token: signed(t, jwt.MapClaims{"sub": "x"}), wantErr: true},
		{name: "user claim not a string", token: signed(t, jwt.MapClaims{"user": 42}), wantErr: true},
		{name: "empty user claim", token: signed(t, jwt.MapClaims{"user": ""}), wantErr: true},
		{name: "malformed token", token: "not-a-jwt", wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := userClaim(tt.token)
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr %v", err, tt.wantErr)
			}
			if got != tt.want {
				t.Errorf("user = %q, want %q", got, tt.want)
			}
		})
	}
}
