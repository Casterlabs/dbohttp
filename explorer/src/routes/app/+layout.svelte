<script lang="ts">
	import { checkSettings, loadSettings } from '$lib/app/stores';
	import { onMount } from 'svelte';
	import { page } from '$app/stores';

	let sidebarItems = [
		{
			icon: 'cube-transparent',
			name: 'Explorer',
			href: '/app/explorer'
		},
		{
			icon: 'command-line',
			name: 'Console',
			href: '/app/console'
		},
		{
			icon: 'cog',
			name: 'Settings',
			href: '/app/settings'
		}
	];

	let ready = false;

	onMount(() => {
		loadSettings();
		checkSettings();
		ready = true;
	});
</script>

<div class="flex flex-col-reverse md:flex-row w-full h-full">
	<div
		class="border-r border-base-4 bg-base-2 w-full h-fit md:w-fit md:h-full p-2 md:p-4 text-center"
	>
		<ul class="space-x-6 md:space-x-0 md:space-y-4">
			{#each sidebarItems as { icon, name, href }}
				{@const isSelected = $page.url.pathname == href}
				<li class="inline-block md:block" title={name}>
					<a
						{href}
						class="inline-block md:block p-2 md:p-4 border border-base-4 bg-base-2 hover:bg-base-5 hover:border-base-7 rounded"
						class:bg-base-6={isSelected}
						class:border-base-7={isSelected}
					>
						<icon data-icon={icon} />
					</a>
				</li>
			{/each}
		</ul>
	</div>
	<div class="flex-1 overflow-x-hidden overflow-y-auto py-2 px-4">
		{#if ready}
			<slot />
		{/if}
	</div>
</div>

<style>
	:global(#dark-light-toggle) {
		top: unset !important;
		right: unset !important;
		bottom: 1.65rem;
		left: 2.25rem;
	}
</style>
