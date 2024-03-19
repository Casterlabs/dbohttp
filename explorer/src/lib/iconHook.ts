import { tick } from 'svelte';

const iconCache: { [key: string]: string } = {};

function replaceIcon(element: Element) {
	if (element.hasAttribute('data-replaced')) return;

	const iconPath = element.getAttribute('data-icon') as string;
	element.setAttribute('data-replaced', 'true');

	if (iconCache[iconPath]) {
		element.innerHTML = iconCache[iconPath];
		console.debug('[Icons]', 'Loaded icon from cache:', iconPath);
	} else {
		(async () => {
			try {
				const svg = await fetch(`/icons/${iconPath}.svg`) //
					.then((res) => {
						if (res.ok) {
							return res.text();
						} else {
							throw 'Status: ' + res.status;
						}
					});

				element.innerHTML = iconCache[iconPath] = svg;
				console.debug('[Icons]', 'Loaded icon:', iconPath);
			} catch (e) {
				element.innerHTML = iconCache[iconPath] =
					'<div class="bg-red-500 h-full w-full text-white" title="MISSING ICON">X</div>'; // Visual error.
				console.error('[Icons]', 'Could not load icon', iconPath, 'due to an error:');
				console.error(e);
			}
		})();
	}
}

export default async function hook() {
	new MutationObserver(async (records) => {
		await tick();
		document.querySelectorAll('icon').forEach(replaceIcon);
	}).observe(document.body, {
		subtree: true,
		attributes: true,
		childList: true
	});

	await tick();
	document.querySelectorAll('icon').forEach(replaceIcon);
}
