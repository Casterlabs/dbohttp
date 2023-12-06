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

	onMount(() => {
		loadSettings();
		checkSettings();
	});
</script>

<div class="flex flex-row w-full h-full">
	<div class="border-r border-base-4 bg-base-2 w-fit h-full p-4">
		<ul class="space-y-4">
			{#each sidebarItems as { icon, name, href }}
				{@const isSelected = $page.url.pathname == href}
				<li title={name}>
					<a
						{href}
						class="block p-4 border border-base-4 bg-base-2 hover:bg-base-5 hover:border-base-7 rounded"
						class:bg-base-6={isSelected}
						class:border-base-7={isSelected}
					>
						<icon data-icon={icon} />
					</a>
				</li>
			{/each}
		</ul>
	</div>
	<div class="flex-1 overflow-auto p-2 pl-4">
		<slot />
	</div>
</div>

<style>
	:global(#dark-light-toggle) {
		top: unset !important;
		right: unset !important;
		bottom: 2.25rem;
		left: 2.25rem;
	}
</style>
