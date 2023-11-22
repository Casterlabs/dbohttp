<script>
	import '$lib/css/app.css';
	import '$lib/css/colors/base.css';
	import '$lib/css/colors/primary.css';
	import '$lib/css/colors/misc.css';

	import iconsHook from '$lib/layout/iconHook';
	import { onMount } from 'svelte';

	let useLightTheme = false;

	onMount(iconsHook);

	onMount(async () => {
		const userPreference = JSON.parse(
			// @ts-ignore
			localStorage.getItem('casterlabs:dbohttp:color_preference')
		);
		const browserPreference = window.matchMedia
			? window.matchMedia('(prefers-color-scheme: dark)')
				? 'dark'
				: 'light'
			: 'dark';

		console.debug('User theme preference:', userPreference);
		console.debug('Browser/system theme preference:', browserPreference);

		if (userPreference) {
			console.log('User has a theme preference, going with that.');
			useLightTheme = userPreference == 'light';
		} else {
			console.log('User has no theme preference yet, using browser/system preference.');
			useLightTheme = browserPreference == 'light';
		}
	});
</script>

<!--
	The sites's theming is handled with data-theme-base, data-theme-primary, and class:dark-theme (we include data-theme-dark for debugging).
	All of the css files to make this happen are imported above.
-->

<div
	id="css-intermediate"
	class="relative w-full h-full bg-base-1 text-base-12 overflow-auto p-2"
	class:dark-theme={!useLightTheme}
	data-theme-dark={!useLightTheme}
>
	<slot />

	<button
		class="absolute translate-y-0.5 top-4 lg:right-4 text-base-11"
		on:click={() => {
			useLightTheme = !useLightTheme;
			console.log('Switching theme to:', useLightTheme ? 'light' : 'dark');
			localStorage.setItem(
				'casterlabs:dbohttp:color_preference',
				JSON.stringify(useLightTheme ? 'light' : 'dark')
			);
		}}
	>
		{#if useLightTheme}
			<icon class="w-5 h-5" data-icon="icon/sun" />
		{:else}
			<icon class="w-5 h-5" data-icon="icon/moon" />
		{/if}
	</button>
</div>

<style>
	#css-intermediate {
		--link: rgb(54, 100, 252);
		--error: rgb(224, 30, 30);
		--success: rgb(69, 204, 69);
	}

	#css-intermediate.dark-theme {
		--link: rgb(58, 137, 255);
		--error: rgb(252, 31, 31);
		--success: rgb(64, 187, 64);
	}
</style>
