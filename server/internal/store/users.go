package store

import (
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"time"

	"golang.org/x/crypto/bcrypt"
)

var (
	ErrNotFound = errors.New("not found")
	ErrConflict = errors.New("conflict")
)

type User struct {
	ID       int64
	Username string
}

func (s *Store) EnsureAdmin(username, password string) error {
	var exists bool
	err := s.db.QueryRow(`SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)`, username).Scan(&exists)
	if err != nil {
		return err
	}
	if exists {
		return nil
	}
	return s.createUserRaw(username, password)
}

func (s *Store) createUserRaw(username, password string) error {
	h, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`INSERT INTO users(username, password_hash, created_at) VALUES(?,?,?)`,
		username, string(h), time.Now().Unix())
	return err
}

func (s *Store) CreateUser(username, password string) (*User, error) {
	var exists bool
	err := s.db.QueryRow(`SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)`, username).Scan(&exists)
	if err != nil {
		return nil, err
	}
	if exists {
		return nil, ErrConflict
	}
	if err := s.createUserRaw(username, password); err != nil {
		return nil, err
	}
	var u User
	err = s.db.QueryRow(`SELECT id, username FROM users WHERE username = ?`, username).Scan(&u.ID, &u.Username)
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func (s *Store) VerifyLogin(username, password string) (*User, error) {
	var u User
	var hash string
	err := s.db.QueryRow(`SELECT id, username, password_hash FROM users WHERE username = ?`, username).
		Scan(&u.ID, &u.Username, &hash)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	if bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) != nil {
		return nil, ErrNotFound
	}
	return &u, nil
}

func (s *Store) CreateToken(userID int64, ttl time.Duration) (string, error) {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	tok := hex.EncodeToString(buf)
	_, err := s.db.Exec(`INSERT INTO tokens(token, user_id, expires_at) VALUES(?,?,?)`,
		tok, userID, time.Now().Add(ttl).Unix())
	return tok, err
}

func (s *Store) UserByToken(token string) (*User, error) {
	if token == "" {
		return nil, ErrNotFound
	}
	var u User
	err := s.db.QueryRow(`SELECT u.id, u.username FROM tokens t JOIN users u ON u.id = t.user_id
		WHERE t.token = ? AND t.expires_at > ?`, token, time.Now().Unix()).Scan(&u.ID, &u.Username)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

func (s *Store) DeleteToken(token string) error {
	_, err := s.db.Exec(`DELETE FROM tokens WHERE token = ?`, token)
	return err
}

func (s *Store) CleanExpiredTokens() error {
	_, err := s.db.Exec(`DELETE FROM tokens WHERE expires_at < ?`, time.Now().Unix())
	return err
}
