import { goto } from '$app/navigation';
import { get, writable } from 'svelte/store';

export const connectionUrl = writable('');
export const connectionPassword = writable('');

export function checkSettings() {
	if (get(connectionUrl).length == 0 || get(connectionPassword).length == 0) {
		goto('/app/settings');
	}
}

export function loadSettings() {
	connectionUrl.set(localStorage.getItem('casterlabs:dbohttp:url') || '');
	connectionPassword.set(localStorage.getItem('casterlabs:dbohttp:password') || '');
}

export function saveSettings() {
	localStorage.setItem('casterlabs:dbohttp:url', get(connectionUrl));
	localStorage.setItem('casterlabs:dbohttp:password', get(connectionPassword));
}
